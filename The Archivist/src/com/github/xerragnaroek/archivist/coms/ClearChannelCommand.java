package com.github.xerragnaroek.archivist.coms;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;

import net.dv8tion.jda.api.entities.TextChannel;

/**
 * 
 */
public class ClearChannelCommand {

	public static CompletableFuture<?> clearChannel(TextChannel tc) {
		AtomicInteger count = new AtomicInteger(0);
		long start = System.currentTimeMillis();
		return clearImpl(tc, count);

	}

	private static CompletableFuture<Void> clearImpl(TextChannel tc, AtomicInteger count) {
		return tc.getHistoryFromBeginning(100).submit().thenAccept(mh -> {
			if (!mh.isEmpty()) {
				List<CompletableFuture<Void>> cfs = tc.purgeMessages(mh.getRetrievedHistory());
				CompletableFuture.allOf(cfs.toArray(new CompletableFuture<?>[cfs.size()])).join();
				count.addAndGet(mh.size());
				clearImpl(tc, count).join();
			}
		});
	}
}
