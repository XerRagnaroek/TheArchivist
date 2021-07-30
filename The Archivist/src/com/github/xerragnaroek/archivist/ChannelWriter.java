package com.github.xerragnaroek.archivist;

import java.io.IOException;
import java.io.Writer;

import net.dv8tion.jda.api.entities.TextChannel;

/**
 * 
 */
public class ChannelWriter extends Writer {
	private TextChannel tc;

	public ChannelWriter(TextChannel chan) {
		tc = chan;
	}

	@Override
	public void write(char[] cbuf, int off, int len) throws IOException {
		tc.sendMessage(String.valueOf(cbuf, off, len)).queue();
	}

	@Override
	public void flush() throws IOException {}

	@Override
	public void close() throws IOException {}
}
