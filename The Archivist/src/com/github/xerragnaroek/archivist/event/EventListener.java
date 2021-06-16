package com.github.xerragnaroek.archivist.event;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.github.xerragnaroek.archivist.Core;
import com.github.xerragnaroek.archivist.archive.Archive;
import com.github.xerragnaroek.archivist.coms.CommandHandler;

import net.dv8tion.jda.api.events.channel.text.TextChannelCreateEvent;
import net.dv8tion.jda.api.events.channel.text.TextChannelDeleteEvent;
import net.dv8tion.jda.api.events.channel.voice.VoiceChannelCreateEvent;
import net.dv8tion.jda.api.events.channel.voice.VoiceChannelDeleteEvent;
import net.dv8tion.jda.api.events.guild.voice.GuildVoiceJoinEvent;
import net.dv8tion.jda.api.events.guild.voice.GuildVoiceLeaveEvent;
import net.dv8tion.jda.api.events.guild.voice.GuildVoiceMoveEvent;
import net.dv8tion.jda.api.events.message.guild.GuildMessageDeleteEvent;
import net.dv8tion.jda.api.events.message.guild.GuildMessageReceivedEvent;
import net.dv8tion.jda.api.events.message.guild.GuildMessageUpdateEvent;
import net.dv8tion.jda.api.events.message.priv.PrivateMessageReceivedEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;

/**
 * 
 */
public class EventListener extends ListenerAdapter {

	private final Logger log = LoggerFactory.getLogger(EventListener.class);

	@Override
	public void onGuildMessageReceived(GuildMessageReceivedEvent event) {
		if (event.getAuthor().getIdLong() != Core.JDA.getSelfUser().getIdLong()) {
			Archive.saveMessage(event);
			CommandHandler.handleCommand(event);
		}
	}

	@Override
	public void onGuildMessageUpdate(GuildMessageUpdateEvent event) {
		if (event.getAuthor().getIdLong() != Core.JDA.getSelfUser().getIdLong()) {
			Archive.saveMessageEdited(event);
		}
	}

	@Override
	public void onPrivateMessageReceived(PrivateMessageReceivedEvent event) {
		if (!event.getAuthor().isBot()) {
			CommandHandler.handleCommand(event);
		}
	}

	@Override
	public void onGuildVoiceJoin(GuildVoiceJoinEvent event) {
		if (!event.getMember().getUser().isBot()) {
			log.debug("{} joined VC {}", event.getMember().getEffectiveName(), event.getChannelJoined().getName());
			Archive.handleUserJoinedChannel(event);
		}
	}

	@Override
	public void onGuildVoiceLeave(GuildVoiceLeaveEvent event) {
		if (!event.getMember().getUser().isBot()) {
			log.debug("{} left VC {}", event.getMember().getEffectiveName(), event.getChannelLeft().getName());
			Archive.handleUserLeftChannel(event);
		}
	}

	@Override
	public void onGuildVoiceMove(GuildVoiceMoveEvent event) {
		if (!event.getMember().getUser().isBot()) {
			log.debug("{} moved from {} to {}", event.getMember().getEffectiveName(), event.getChannelLeft().getName(), event.getChannelJoined().getName());
			Archive.handleUserMovedChannel(event);
		}
	}

	@Override
	public void onTextChannelDelete(TextChannelDeleteEvent event) {
		Archive.handleTextChannelDeleted(event);
	}

	@Override
	public void onTextChannelCreate(TextChannelCreateEvent event) {
		Archive.handleTextChannelCreated(event);
	}

	@Override
	public void onVoiceChannelDelete(VoiceChannelDeleteEvent event) {
		Archive.handleVoiceChannelDeleted(event);
	}

	@Override
	public void onVoiceChannelCreate(VoiceChannelCreateEvent event) {
		Archive.handleVoiceChannelCreated(event);
	}

	@Override
	public void onGuildMessageDelete(GuildMessageDeleteEvent event) {
		Archive.saveMessageDeleted(event);
	}
}
