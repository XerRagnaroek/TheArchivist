package com.github.xerragnaroek.archivist.coms;

import net.dv8tion.jda.api.events.interaction.SlashCommandEvent;
import net.dv8tion.jda.api.events.message.guild.GuildMessageReceivedEvent;
import net.dv8tion.jda.api.events.message.priv.PrivateMessageReceivedEvent;
import net.dv8tion.jda.api.interactions.commands.build.CommandData;

/**
 * 
 */
public interface Command {

	public String getName();

	public CommandData getCommandData();

	default public boolean isSlashCommand() {
		return true;
	}

	public void slashExec(SlashCommandEvent e);

	public void guildExec(GuildMessageReceivedEvent e);

	public void pmExec(PrivateMessageReceivedEvent e);
}
