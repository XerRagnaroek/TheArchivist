package com.github.xerragnaroek.archivist.archive;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Future;
import java.util.stream.Collectors;

import javax.sound.sampled.AudioFileFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.github.xerragnaroek.archivist.Core;
import com.github.xerragnaroek.util.io.DirectoryInputStream;

import net.dv8tion.jda.api.audio.AudioReceiveHandler;
import net.dv8tion.jda.api.audio.CombinedAudio;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.entities.VoiceChannel;
import net.dv8tion.jda.api.managers.AudioManager;

/**
 * 
 */
public class Recorder implements AudioReceiveHandler {

	private AudioManager am;
	private VoiceChannel vc;
	private Archive archive;
	private ZonedDateTime start;
	private Path file;
	private final Logger log;
	private Saver saver;
	private Path speakLog;
	private Set<User> speakers = new HashSet<>();
	private int audioPacketsReceived = 0;

	public Recorder(Archive arch, AudioManager am, VoiceChannel vc) {
		archive = arch;
		this.am = am;
		this.vc = vc;
		log = LoggerFactory.getLogger(Recorder.class + "#" + vc.getIdLong());
		init();
	}

	private void init() {
		start = ZonedDateTime.now(Core.GB);
		file = Path.of(archive.voiceChannelPath.get(vc.getIdLong()).toString(), "/" + DateTimeFormatter.ofPattern("HH-mm-ss").format(start) + "/");
		speakLog = Path.of(file.toString(), "log.csv");
	}

	public void startRecording() {
		log.debug("Starting recording");
		try {
			Files.writeString(speakLog, "timestamp,id,event", StandardOpenOption.CREATE, StandardOpenOption.APPEND);
			if (!Files.exists(file)) {
				Files.createDirectory(file);
			}
			saver = new Saver(file);
			am.setReceivingHandler(this);
			am.openAudioConnection(vc);
			saver.start();
		} catch (IOException e) {
			log.error("", e);
		}
	}

	public void stopRecording() {
		log.debug("Stopping recording");
		am.closeAudioConnection();
		am.setReceivingHandler(null);
		Scribes.freeScribe(am.getJDA());
		saver.stop();
		// while (!saver.isDone()) {}
		// log.debug("Saver is done!");
		// Path renamed = Path.of(file.toString().replace(".pcm", " - " +
		// timeStampFormat.format(ZonedDateTime.now(Core.GB)) + ".pcm"));
		// Files.move(file, renamed);
		// log.debug("{} has been renamed to {}", file.getFileName(), renamed.getFileName());
	}

	@Override
	public boolean canReceiveCombined() {
		return true;
	}

	@Override
	public void handleCombinedAudio(CombinedAudio combinedAudio) {
		saver.addData(combinedAudio.getAudioData(1));
		if (++audioPacketsReceived >= 25) {
			handleSpeakingUsers(combinedAudio.getUsers());
			audioPacketsReceived = 0;
		}
	}

	private void handleSpeakingUsers(List<User> users) {
		Guild g = Core.JDA.getGuildById(archive.gId);
		users.forEach(u -> {
			if (!speakers.contains(u)) {
				speakers.add(u);
				String str = String.format("[%s]%s started speaking", u.getId(), g.getMember(u).getEffectiveName()) + "\n";
				log(str);
			}
		});
		speakers.forEach(u -> {
			if (!users.contains(u)) {
				Member m = g.getMember(u);
				if (m == null) {
					m = Core.JDA.getGuildById(g.getId()).getMember(u);
				}
				if (m != null) {
					String str = String.format("%s,%s stopped speaking", u.getId(), m.getEffectiveName()) + "\n";
					log(str);
				}
			}
		});
		speakers.retainAll(users);
	}

	void userJoinChannel(Member m) {
		String str = String.format("%s,%s joined the channel", m.getId(), m.getEffectiveName()) + "\n";
		log(str);
	}

	void userLeftChannel(Member m) {
		String str = String.format("%s,%s left the channel", m.getId(), m.getEffectiveName()) + "\n";
		log(str);
	}

	private void log(String str) {
		str = String.format("%s,%s", ZonedDateTime.now(Core.GB).format(Archive.LOG_FORMATTER), str);
		try {
			Files.writeString(speakLog, str, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
		} catch (IOException e) {
			log.error("", e);
		}
	}
}

class Saver {
	private List<byte[]> data = new LinkedList<>();
	private boolean run = true;
	private final Logger log = LoggerFactory.getLogger(Saver.class);
	private Future<?> f;
	private int runningFileNum = 1;
	private Path base;

	Saver(Path f) throws IOException {
		base = f;
	}

	void addData(byte[] d) {
		if (run) {
			synchronized (data) {
				data.add(d);
			}
		}
	}

	void start() {
		f = Core.EXEC.submit(() -> run());
	}

	private void run() {
		while (run) {
			saveQueue();
			try {
				Thread.sleep(5000);
			} catch (InterruptedException e) {
				log.error("", e);
			}
		}
		saveQueue();
		mergeFiles();
	}

	private void saveQueue() {
		if (data.size() > 0) {
			log.debug("Saving {} parts", data.size());
			List<byte[]> tmp;
			synchronized (data) {
				tmp = data;
				data = new LinkedList<>();
			}
			int size = tmp.stream().collect(Collectors.summingInt(b -> b.length));
			byte[] data = new byte[size];
			int i = 0;
			for (byte[] b : tmp) {
				for (int n = 0; n < b.length; n++) {
					data[i++] = b[n];
				}
			}
			try {
				saveToFile(data);
				log.debug("Saved {} kilobytes of audio", size / 1000);
			} catch (IOException e) {
				log.error("", e);
			}
		}
	}

	private void saveToFile(byte[] data) throws IOException {
		Files.write(Path.of(base.toString(), (runningFileNum++) + ".pcm"), data, StandardOpenOption.CREATE);
	}

	private void mergeFiles() {
		try {
			/*
			 * Path outP = Path.of(base.toString(), base.getFileName() + ".pcm");
			 * OutputStream out = Files.newOutputStream(outP, StandardOpenOption.CREATE);
			 * log.debug("Merging {} files into {}", runningFileNum - 1, outP.getFileName().toString());
			 * AtomicLong size = new AtomicLong(0);
			 * Files.list(base).filter(p -> !p.getFileName().equals(outP.getFileName())).sorted((p1, p2) -> {
			 * try {
			 * return Files.getLastModifiedTime(p1).compareTo(Files.getLastModifiedTime(p2));
			 * } catch (IOException e) {
			 * return -1;
			 * }
			 * }).map(t -> {
			 * try {
			 * log.debug(t.getFileName().toString());
			 * size.addAndGet(Files.size(t));
			 * return Files.newInputStream(t);
			 * } catch (IOException e) {
			 * return InputStream.nullInputStream();
			 * }
			 * }).forEach(is -> {
			 * try {
			 * is.transferTo(out);
			 * } catch (IOException e) {
			 * log.error("", e);
			 * }
			 * });
			 * log.debug("Merged!");
			 */
			DirectoryInputStream dis = new DirectoryInputStream(base);
			dis.filter(p -> p.getFileName().toString().endsWith(".pcm"));
			dis.initialize();
			Path wav = Path.of(base.toString(), base.getFileName().toString() + ".wav");
			Files.createFile(wav);
			AudioSystem.write(new AudioInputStream(dis, AudioReceiveHandler.OUTPUT_FORMAT, dis.getExpectedLength()), AudioFileFormat.Type.WAVE, wav.toFile());
			log.debug("Saved wav to {}", wav.getFileName().toString());
			Files.list(base).filter(p -> !p.getFileName().toString().equals(wav.getFileName().toString())).filter(p -> !p.getFileName().toString().endsWith(".log")).forEach(t -> {
				try {
					Files.delete(t);
				} catch (IOException e) {
					// this is try block hell, help me
					log.error("", e);
				}
			});

			log.debug("Deleted old files");
		} catch (IOException e) {
			log.error("", e);
		}
	}

	void stop() {
		run = false;
	}

	boolean isDone() {
		return f.isDone();
	}

}
