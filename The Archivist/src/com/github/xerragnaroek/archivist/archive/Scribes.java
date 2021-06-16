package com.github.xerragnaroek.archivist.archive;

import java.util.Map;
import java.util.Map.Entry;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;

import javax.security.auth.login.LoginException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.requests.GatewayIntent;
import net.dv8tion.jda.api.utils.MemberCachePolicy;
import net.dv8tion.jda.api.utils.cache.CacheFlag;

/**
 * 
 */
public class Scribes {
	private static final Map<Integer, JDA> availableScribes = new TreeMap<>();
	private static final Map<JDA, Integer> scribes = new ConcurrentHashMap<>();
	private static final Logger log = LoggerFactory.getLogger(Scribes.class);

	public static void buildScribes(String[] tokens) throws LoginException {
		int i = 0;
		for (String token : tokens) {
			JDA scribe = JDABuilder.createLight(token, GatewayIntent.GUILD_VOICE_STATES, GatewayIntent.GUILD_MEMBERS).enableCache(CacheFlag.VOICE_STATE).setMemberCachePolicy(MemberCachePolicy.DEFAULT).build();
			availableScribes.put(i++, scribe);
			scribes.put(scribe, i);
		}
		log.info("Built {} scribes", scribes.size());
	}

	public static void shutdownScribes() {
		scribes.keySet().forEach(JDA::shutdown);
	}

	public static boolean hasScribeAvailable() {
		return !availableScribes.isEmpty();
	}

	public static JDA getScribe() {
		Entry<Integer, JDA> first = availableScribes.entrySet().stream().findFirst().get();
		JDA scribe = first.getValue();
		availableScribes.remove(first.getKey());
		return scribe;
	}

	public static void freeScribe(JDA scribe) {
		availableScribes.put(scribes.get(scribe), scribe);
	}
}
