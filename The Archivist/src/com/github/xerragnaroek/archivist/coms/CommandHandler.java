package com.github.xerragnaroek.archivist.coms;

import java.util.HashMap;

import com.github.xerragnaroek.archivist.Core;

import net.dv8tion.jda.api.events.interaction.SlashCommandEvent;
import net.dv8tion.jda.api.events.message.guild.GuildMessageReceivedEvent;
import net.dv8tion.jda.api.events.message.priv.PrivateMessageReceivedEvent;
import net.dv8tion.jda.api.requests.restaction.CommandListUpdateAction;

/**
 * 
 */
public class CommandHandler {

	private static HashMap<String, Command> coms = new HashMap<>();
	private static Command[] tmp = new Command[] { new ReadOnlyCommand(), new StopCommand() };
	static {
		for (Command com : tmp) {
			coms.put(com.getName(), com);
		}
	}

	public static void handleCommand(GuildMessageReceivedEvent e) {
		Command com = findCom(e.getMessage().getContentRaw());
		if (com != null && !com.isSlashCommand()) {
			com.guildExec(e);
		}
	}

	public static void handleCommand(PrivateMessageReceivedEvent e) {
		Command com = findCom(e.getMessage().getContentRaw());
		if (com != null && !com.isSlashCommand()) {
			com.pmExec(e);
		}
	}

	public static void handleCommand(SlashCommandEvent e) {
		Command com = coms.get(e.getName());
		com.slashExec(e);
	}

	private static Command findCom(String content) {
		if (content.startsWith("!")) {
			String com = content.substring(1).split(" ")[0];
			return coms.get(com);
		}
		return null;
	}

	public static void updateCommands() {
		CommandListUpdateAction commands = Core.JDA.updateCommands();
		commands.addCommands(coms.get("read_only").getCommandData());
		commands.queue(v -> System.out.println("Commands pushed to api!"));
	}

}
