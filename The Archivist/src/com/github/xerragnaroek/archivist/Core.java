package com.github.xerragnaroek.archivist;

import java.io.PrintStream;
import java.nio.charset.Charset;
import java.time.ZoneId;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Scanner;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.stream.Stream;

import javax.security.auth.login.LoginException;

import org.apache.commons.collections4.IteratorUtils;
import org.apache.commons.io.output.WriterOutputStream;

import com.github.xerragnaroek.archivist.archive.Archive;
import com.github.xerragnaroek.archivist.archive.Scribes;
import com.github.xerragnaroek.archivist.event.EventListener;

import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.requests.GatewayIntent;
import net.dv8tion.jda.api.utils.cache.CacheFlag;

/**
 * 
 */
public class Core {
	public static JDA JDA;
	public static final ScheduledExecutorService EXEC = Executors.newScheduledThreadPool(Runtime.getRuntime().availableProcessors());
	public static final List<Long> VIPS = new LinkedList<>();
	public static final ZoneId GB = ZoneId.of("Europe/London");

	public static void main(String[] args) throws LoginException, InterruptedException {
		handleArgs(args);
		Archive.init();
	}

	private static void buildJDA(String token) throws LoginException, InterruptedException {
		JDABuilder bob = JDABuilder.createDefault(token);
		bob.enableIntents(GatewayIntent.GUILD_MEMBERS);
		bob.setEventPool(EXEC);
		// bob.setAudioPool(EXEC);
		// bob.setMemberCachePolicy(MemberCachePolicy.ONLINE);
		bob.enableCache(CacheFlag.VOICE_STATE);
		JDA = bob.build();
		JDA.awaitReady();
		JDA.setEventManager(new InterfacedEvendManager());
		JDA.addEventListener(new EventListener());
		System.setErr(new PrintStream(new WriterOutputStream(new ChannelWriter(JDA.getGuildById("864067881746956298").getTextChannelById("870748668955877456")), Charset.defaultCharset(), 1024, true)));
		// CommandHandler.updateCommands();
		EXEC.execute(() -> {
			Scanner scan = new Scanner(System.in);
			String in;
			while (!(in = scan.next()).equalsIgnoreCase("stop") && !in.equalsIgnoreCase("q")) {
				System.out.println("stop or q to exit!");
			}
			System.out.println("Shutting down the Archivist and his scribes.");
			Core.JDA.shutdown();
			Scribes.shutdownScribes();
			System.exit(0);
		});

	}

	private static void handleArgs(String args[]) {
		Iterator<String> it = IteratorUtils.arrayIterator(args);
		while (it.hasNext()) {
			handleArg(it, it.next());
		}
	}

	private static void handleArg(Iterator<String> it, String arg) {
		try {
			switch (arg) {
				case "-token":
					buildJDA(it.next());
					break;
				case "-dev_id":
					String[] split = it.next().split(",");
					Stream.of(split).map(Long::parseLong).forEach(l -> VIPS.add(l));
					break;
				case "-scribes":
					Scribes.buildScribes(it.next().split(","));
					break;
				default:
					throw new IllegalArgumentException("Unrecognized option '" + arg + "'");
			}
		} catch (Exception e) {
			throw new IllegalArgumentException("Unrecognized option '" + arg + "'");
		}
	}

}
