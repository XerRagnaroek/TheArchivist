package com.github.xerragnaroek.archivist.coms;

import java.util.EnumSet;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.events.interaction.SlashCommandEvent;
import net.dv8tion.jda.api.events.message.guild.GuildMessageReceivedEvent;
import net.dv8tion.jda.api.events.message.priv.PrivateMessageReceivedEvent;
import net.dv8tion.jda.api.interactions.commands.build.CommandData;

public class ReadOnlyCommand implements Command {

	private final Logger log = LoggerFactory.getLogger(ReadOnlyCommand.class);
	private final EnumSet<Permission> readOnly = EnumSet.of(Permission.MESSAGE_READ, Permission.MESSAGE_ADD_REACTION, Permission.NICKNAME_CHANGE, Permission.VIEW_CHANNEL, Permission.MESSAGE_HISTORY);
	private final EnumSet<Permission> everyone = EnumSet.of(Permission.MESSAGE_READ, Permission.MESSAGE_WRITE, Permission.MESSAGE_HISTORY, Permission.MESSAGE_ATTACH_FILES, Permission.MESSAGE_ADD_REACTION, Permission.NICKNAME_CHANGE, Permission.VIEW_CHANNEL, Permission.VOICE_CONNECT, Permission.VOICE_SPEAK, Permission.VOICE_STREAM, Permission.VOICE_USE_VAD);

	@Override
	public String getName() {
		return "read_only";
	}

	@Override
	public CommandData getCommandData() {
		return new CommandData("read_only", "Toggles this server's read only mode.");
	}

	@Override
	public void slashExec(SlashCommandEvent e) {
		Guild g = e.getGuild();
		Role everyone = g.getPublicRole();
		if (everyone.getPermissions().equals(readOnly)) {
			log.debug("Opening server back up from read only!");
			everyone.getManager().setPermissions(this.everyone).complete();
			e.reply("Read-only mode has been disabled!").queue();
		} else {
			log.debug("Setting server to read only");
			everyone.getManager().setPermissions(readOnly).complete();
			e.reply("Server is now read-only!").queue();
		}
	}

	@Override
	public void guildExec(GuildMessageReceivedEvent e) {
		Guild g = e.getGuild();
		Role everyone = g.getPublicRole();
		if (everyone.getPermissions().equals(readOnly)) {
			log.debug("Opening server back up from read only!");
			everyone.getManager().setPermissions(this.everyone).complete();
			e.getMessage().reply("Read-only mode has been disabled!").queue();
		} else {
			log.debug("Setting server to read only");
			everyone.getManager().setPermissions(readOnly).complete();
			e.getMessage().reply("Server is now read-only!").queue();
		}
	}

	@Override
	public void pmExec(PrivateMessageReceivedEvent e) {}
}
