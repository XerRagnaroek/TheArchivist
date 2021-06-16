package com.github.xerragnaroek.archivist.archive;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.github.xerragnaroek.archivist.Core;

import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.TextChannel;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.entities.VoiceChannel;
import net.dv8tion.jda.api.events.channel.text.TextChannelCreateEvent;
import net.dv8tion.jda.api.events.channel.text.TextChannelDeleteEvent;
import net.dv8tion.jda.api.events.channel.voice.VoiceChannelCreateEvent;
import net.dv8tion.jda.api.events.channel.voice.VoiceChannelDeleteEvent;
import net.dv8tion.jda.api.events.guild.voice.GuildVoiceJoinEvent;
import net.dv8tion.jda.api.events.guild.voice.GuildVoiceLeaveEvent;
import net.dv8tion.jda.api.events.guild.voice.GuildVoiceMoveEvent;
import net.dv8tion.jda.api.events.message.guild.GuildMessageReceivedEvent;
import net.dv8tion.jda.api.events.message.guild.GuildMessageUpdateEvent;
import net.dv8tion.jda.api.managers.AudioManager;

/**
 * Class that does the archiving.
 */
public class Archive {
	// for possible multi guild operation
	private static HashMap<Long, Archive> archives = new HashMap<>();
	private static DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd-MM-yy HH:mm:ss.SSS");

	// fields are package visible to be accessible to Recorders
	Path baseLoc;
	HashMap<Long, Path> textChannelPath = new HashMap<>();
	HashMap<Long, Path> voiceChannelPath = new HashMap<>();
	private long gId;
	private final Logger log;
	private Map<Long, Recorder> recorder = new ConcurrentHashMap<>();

	private Archive(long gId) {
		this.gId = gId;
		log = LoggerFactory.getLogger(Archive.class + "#" + gId);
		initArchive();
	}

	private void initArchive() {
		log.debug("Setting up archive");
		Guild g = Core.JDA.getGuildById(gId);
		ZonedDateTime now = ZonedDateTime.now(ZoneId.of("Europe/London"));
		baseLoc = Path.of("./archive/" + g.getName() + "/" + DateTimeFormatter.ofPattern("dd.MM.yyyy").format(now) + "/");
		log.debug("baseLoc = {}", baseLoc.toAbsolutePath().toString());
		try {
			initTextChannels(g);
			initVoiceChannels(g);
		} catch (IOException e) {
			log.error("", e);
		}
	}

	private void initTextChannels(Guild g) throws IOException {
		Path tmpBase = Path.of(baseLoc.toString(), "/text/");
		if (Files.notExists(tmpBase)) {
			Files.createDirectories(tmpBase);
		}
		g.getTextChannelCache().forEach(this::initTextChannelImpl);
	}

	private void initTextChannelImpl(TextChannel tc) {
		Path p = Path.of(baseLoc.toString(), "/text/" + tc.getName() + "[" + tc.getId() + "]" + "/log.log");
		try {
			if (Files.notExists(p)) {
				Files.createDirectories(p.getParent());
				Files.createFile(p);
			}
			Path att = Path.of(p.getParent().toString(), "/files/");
			if (Files.notExists(att)) {
				Files.createDirectory(att);
			}
			textChannelPath.put(tc.getIdLong(), p);
		} catch (IOException e) {
			log.error("", e);
		}
	}

	private void initVoiceChannels(Guild g) throws IOException {
		Path tmpBase = Path.of(baseLoc.toString(), "/voice/");
		if (Files.notExists(tmpBase)) {
			Files.createDirectories(tmpBase);
		}
		g.getVoiceChannelCache().forEach(this::initVoiceChannelImpl);
	}

	private void initVoiceChannelImpl(VoiceChannel vc) {
		Path p = Path.of(baseLoc.toString(), "/voice/" + vc.getName() + "[" + vc.getId() + "]" + "/");
		try {
			if (Files.notExists(p)) {
				Files.createDirectory(p);
			}
			voiceChannelPath.put(vc.getIdLong(), p);
		} catch (IOException e) {
			log.error("", e);
		}
	}

	private void saveMessageImpl(GuildMessageReceivedEvent event) {
		long start = System.currentTimeMillis();
		Path p = textChannelPath.get(event.getChannel().getIdLong());
		Message m = event.getMessage();
		appendToFile(p, m, event.getAuthor(), m.getContentRaw());
		m.getAttachments().forEach(a -> {
			log.debug(a.getFileName());
			Path tmp = Path.of(p.getParent().toString(), "/files/" + a.getFileName());
			/*
			 * if (Files.notExists(tmp.getParent())) {
			 * try {
			 * Files.createDirectory(tmp.getParent());
			 * } catch (IOException e) {
			 * log.error("", e);
			 * }
			 * }
			 */
			int i = 1;
			while (Files.exists(tmp)) {
				String[] fN = a.getFileName().split("\\.");
				tmp = Path.of(p.getParent().toString(), "/files/" + fN[0] + "(" + i + ")." + fN[1]);
				i++;
			}
			try {
				Files.createFile(tmp);
				a.downloadToFile(tmp.toAbsolutePath().toString()).whenComplete((f, t) -> {
					if (t == null) {
						log.debug("Saved file: {}", f.toString());
					} else {
						log.error("", t);
					}
				});
				appendToFile(p, m, event.getAuthor(), String.format("attached file \"%s\"", a.getFileName()));
			} catch (IOException e) {
				log.error("", e);
			}
		});
		log.debug("Saved message in {} ms", (System.currentTimeMillis() - start));
	}

	private void editMessageImpl(GuildMessageUpdateEvent e) {
		long start = System.currentTimeMillis();
		Path p = textChannelPath.get(e.getChannel().getIdLong());
		appendToFile(p, e.getMessage(), e.getAuthor(), "edited message: \"" + e.getMessage().getContentRaw() + "\"");
		log.debug("Saved message in {} ms", (System.currentTimeMillis() - start));
	}

	private void appendToFile(Path p, Message m, User u, String content) {
		String str = String.format("[%s][%s]%s:%s", m.getTimeCreated().atZoneSameInstant(Core.GB).format(formatter), m.getId(), u.getAsTag(), content) + "\n";
		try {
			Files.writeString(p, str, StandardOpenOption.APPEND);
		} catch (IOException e) {
			log.error("", e);
		}
	}

	private boolean isRecording(VoiceChannel vc) {
		return recorder.containsKey(vc.getIdLong());
	}

	private void renameDeletedFolder(Path folder) {
		Path renamed = folder.resolveSibling(folder.getFileName() + "[deleted-" + DateTimeFormatter.ofPattern("dd-MM-yy-'T'HH-mm-ss").format(ZonedDateTime.now(Core.GB)) + "]");
		try {
			Files.move(folder, renamed);
			log.debug("Renamed folder {} to {}", folder.getFileName(), renamed.getFileName());
		} catch (IOException e) {
			log.error("", e);
		}
	}

	private void startRecImpl(AudioManager am, VoiceChannel vc) {
		if (!recorder.containsKey(vc.getIdLong())) {
			Recorder rec = new Recorder(this, am, vc);
			recorder.put(vc.getIdLong(), rec);
			rec.startRecording();
		}
	}

	private void stopRecImpl(VoiceChannel vc) {
		recorder.computeIfPresent(vc.getIdLong(), (id, rec) -> {
			rec.stopRecording();
			return null;
		});
	}

	private void textChannelCreatedImpl(TextChannel tc) {
		initTextChannelImpl(tc);
	}

	private void textChannelDelImpl(TextChannel tc) {
		Path tcP = textChannelPath.remove(tc.getIdLong());
		try {
			Files.writeString(tcP, String.format("[%s]DELETED", formatter.format(ZonedDateTime.now(Core.GB))), StandardOpenOption.APPEND);
		} catch (IOException e) {
			log.error("", e);
		}
		renameDeletedFolder(tcP.getParent());
	}

	private void voiceChannelCreatedImpl(VoiceChannel vc) {
		initVoiceChannelImpl(vc);
	}

	private void voiceChannelDelImpl(VoiceChannel vc) {
		stopRecImpl(vc);
		Path vcP = voiceChannelPath.remove(vc.getIdLong());
		renameDeletedFolder(vcP);
	}

	public static void init() {
		Core.JDA.getGuilds().forEach(g -> archives.put(g.getIdLong(), new Archive(g.getIdLong())));
		startMidnightThread();
	}

	public static void handleTextChannelCreated(TextChannelCreateEvent event) {
		archives.get(event.getGuild().getIdLong()).textChannelCreatedImpl(event.getChannel());
	}

	public static void handleTextChannelDeleted(TextChannelDeleteEvent event) {
		archives.get(event.getGuild().getIdLong()).textChannelDelImpl(event.getChannel());
	}

	public static void handleUserJoinedChannel(GuildVoiceJoinEvent event) {
		userJoinImpl(event.getGuild(), event.getChannelJoined());
	}

	private static void userJoinImpl(Guild g, VoiceChannel vc) {
		Archive arch = archives.get(g.getIdLong());
		if (!arch.isRecording(vc) && Scribes.hasScribeAvailable()) {
			JDA scribe = Scribes.getScribe();
			g = scribe.getGuildById(g.getId());
			vc = g.getVoiceChannelById(vc.getId());
			arch.startRecImpl(g.getAudioManager(), vc);
		}
	}

	public static void handleUserLeftChannel(GuildVoiceLeaveEvent event) {
		userLeftImpl(event.getChannelLeft());
	}

	private static void userLeftImpl(VoiceChannel vc) {
		if (vc.getMembers().size() == 1) {
			archives.get(vc.getGuild().getIdLong()).stopRecImpl(vc);
		}
	}

	public static void handleUserMovedChannel(GuildVoiceMoveEvent event) {
		userLeftImpl(event.getChannelLeft());
		try {
			Thread.sleep(250);
		} catch (InterruptedException e) {}
		userJoinImpl(event.getGuild(), event.getChannelJoined());
	}

	public static void handleVoiceChannelCreated(VoiceChannelCreateEvent event) {
		archives.get(event.getGuild().getIdLong()).voiceChannelCreatedImpl(event.getChannel());
	}

	public static void handleVoiceChannelDeleted(VoiceChannelDeleteEvent event) {
		archives.get(event.getGuild().getIdLong()).voiceChannelDelImpl(event.getChannel());

	}

	public static void saveEditMessage(GuildMessageUpdateEvent e) {
		archives.get(e.getGuild().getIdLong()).editMessageImpl(e);
	}

	public static void saveMessage(GuildMessageReceivedEvent event) {
		archives.get(event.getGuild().getIdLong()).saveMessageImpl(event);
	}

	private static void startMidnightThread() {
		ZonedDateTime now = ZonedDateTime.now(ZoneId.of("Europe/London"));
		// + 1 cause end is excluded and +10 so it's at 00:00:10, which guarantees than any LocalDate.now()
		// calls is actually the next day and not still the last one
		long untilMidnight = now.until(now.plusDays(1).withHour(0).truncatedTo(ChronoUnit.HOURS), ChronoUnit.SECONDS) + 11;
		Core.EXEC.scheduleAtFixedRate(() -> archives.values().forEach(Archive::initArchive), untilMidnight, TimeUnit.DAYS.toSeconds(1), TimeUnit.SECONDS);
	}

}
