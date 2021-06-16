package com.github.xerragnaroek.archivist.coms;

import com.github.xerragnaroek.archivist.Core;
import com.github.xerragnaroek.archivist.archive.Scribes;

import net.dv8tion.jda.api.events.interaction.SlashCommandEvent;
import net.dv8tion.jda.api.events.message.guild.GuildMessageReceivedEvent;
import net.dv8tion.jda.api.events.message.priv.PrivateMessageReceivedEvent;
import net.dv8tion.jda.api.interactions.commands.build.CommandData;

/**
 * 
 */
public class StopCommand implements Command {

	@Override
	public String getName() {
		return "stop";
	}

	@Override
	public CommandData getCommandData() {
		return null;
	}

	@Override
	public boolean isSlashCommand() {
		return false;
	}

	private void stop(long id) {
		if (Core.VIPS.contains(id)) {
			Core.JDA.shutdown();
			Scribes.shutdownScribes();
			System.exit(0);
		}
	}

	@Override
	public void slashExec(SlashCommandEvent e) {}

	@Override
	public void guildExec(GuildMessageReceivedEvent e) {
		stop(e.getAuthor().getIdLong());
	}

	@Override
	public void pmExec(PrivateMessageReceivedEvent e) {
		stop(e.getAuthor().getIdLong());
	}

}
