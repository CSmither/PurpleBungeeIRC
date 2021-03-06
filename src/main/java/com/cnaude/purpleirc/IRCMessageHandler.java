/*
 * Copyright (C) 2014 - 2017 cnaude
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.cnaude.purpleirc;

import net.md_5.bungee.util.CaseInsensitiveMap;
import com.google.common.base.Joiner;
import java.text.Collator;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import net.md_5.bungee.api.config.ServerInfo;
import org.pircbotx.Channel;
import org.pircbotx.User;

/**
 *
 * @author cnaude
 */
public class IRCMessageHandler {

    PurpleIRC plugin;

    /**
     *
     * @param plugin
     */
    public IRCMessageHandler(PurpleIRC plugin) {
        this.plugin = plugin;
    }

    /**
     *
     * @param ircBot
     * @param user
     * @param channel
     * @param message
     * @param privateMessage
     */
    public void processMessage(PurpleBot ircBot, User user, Channel channel, String message, boolean privateMessage) {
        plugin.logDebug("processMessage: " + message);
        String channelName = channel.getName();
        if (ircBot.muteList.get(channelName).contains(user.getNick())) {
            plugin.logDebug("User is muted. Ignoring message from " + user.getNick() + ": " + message);
            return;
        }
        plugin.logDebug("commandPrefix.length(): " + ircBot.commandPrefix.length());
        String command = message.split(" ")[0];
        if (command.length() > ircBot.commandPrefix.length()) {
            command = command.substring(ircBot.commandPrefix.length());
        }

        if (message.startsWith(ircBot.commandPrefix) && command.matches("^\\w.*")) {

            String commandArgs = null;
            if (message.contains(" ")) {
                commandArgs = message.split(" ", 2)[1];
            }
            plugin.logDebug("IRC command detected: " + command);

            plugin.logDebug(message);
            String target = channel.getName();
            if (ircBot.commandMap.get(channelName).containsKey(command)) {
                boolean privateListen = Boolean.parseBoolean(ircBot.commandMap
                        .get(channelName).get(command).get("private_listen"));
                boolean channelListen = Boolean.parseBoolean(ircBot.commandMap
                        .get(channelName).get(command).get("channel_listen"));
                plugin.logDebug("privateListen: " + privateListen);
                plugin.logDebug("channelListen: " + channelListen);
                if (privateMessage && !privateListen) {
                    plugin.logDebug("This is a private message but privateListen is false. Ignoring...");
                    return;
                }
                if (!privateMessage && !channelListen) {
                    plugin.logDebug("This is a channel message but channelListen is false. Ignoring...");
                    return;
                }

                String gameCommand = (String) ircBot.commandMap.get(channelName).get(command).get("game_command");
                String modes = (String) ircBot.commandMap.get(channelName).get(command).get("modes");
                String perm = (String) ircBot.commandMap.get(channelName).get(command).get("perm");
                boolean privateCommand = Boolean.parseBoolean(ircBot.commandMap.get(channelName).get(command).get("private"));
                IRCMessage.Type responseType = IRCMessage.Type.MESSAGE;
                if (Boolean.parseBoolean(ircBot.commandMap.get(channelName).get(command).get("ctcp"))) {
                    responseType = IRCMessage.Type.CTCP;
                }
                if (Boolean.parseBoolean(ircBot.commandMap.get(channelName).get(command).get("notice"))) {
                    responseType = IRCMessage.Type.NOTICE;
                }
                plugin.logDebug("  ctcp: " + ircBot.commandMap.get(channelName).get(command).get("ctcp"));
                plugin.logDebug("  notice: " + ircBot.commandMap.get(channelName).get(command).get("notice"));
                plugin.logDebug("  private: " + ircBot.commandMap.get(channelName).get(command).get("private"));

                plugin.logDebug(gameCommand + ":" + modes + ":" + privateCommand);

                if (privateCommand || privateMessage) {
                    target = user.getNick();
                }

                plugin.logDebug("Target: " + target);

                if (isValidMode(modes, user, channel)) {
                    switch (gameCommand) {
                        case "@list":
                            if (plugin.listSingleLine) {
                                String concatList = "";
                                for (ServerInfo si : plugin.getProxy().getServers().values()) {
                                    concatList += plugin.getMCPlayers(si, ircBot, channelName);
                                    
                                }
                                sendMessage(ircBot, target, concatList, responseType);

                            } else {
                                for (ServerInfo si : plugin.getProxy().getServers().values()) {
                                    sendMessage(ircBot, target, plugin.getMCPlayers(si, ircBot, channelName), responseType);
                                }
                            }

                            break;
                        case "@uptime":
                            sendMessage(ircBot, target, plugin.getMCUptime(), responseType);
                            break;
                        case "@help":
                            sendMessage(ircBot, target, getCommands(ircBot.commandMap, channelName), responseType);
                            break;
                        case "@chat":
                            ircBot.broadcastChat(user, channel, commandArgs, false);
                            break;
                        case "@ochat":
                            ircBot.broadcastChat(user, channel, commandArgs, true);
                            break;
                        case "@msg":
                            ircBot.playerChat(user, channel, target, commandArgs);
                            break;
                        case "@clearqueue":
                            sendMessage(ircBot, target, ircBot.messageQueue.clearQueue(), responseType);
                            break;
                        case "@query":
                            sendMessage(ircBot, target, plugin.getRemotePlayers(commandArgs), responseType);
                            break;
                        default:
                            if (commandArgs == null) {
                                commandArgs = "";
                            }
                            if (gameCommand.contains("%ARGS%")) {
                                gameCommand = gameCommand.replace("%ARGS%", commandArgs);
                            }
                            if (gameCommand.contains("%NAME%")) {
                                gameCommand = gameCommand.replace("%NAME%", user.getNick());
                            }
                            plugin.logDebug("GM: \"" + gameCommand.trim() + "\"");
                            try {
                                plugin.commandQueue.add(new IRCCommand(new IRCCommandSender(ircBot, target, plugin, responseType), gameCommand.trim()));
                            } catch (Exception ex) {
                                plugin.logError(ex.getMessage());
                            }
                            break;
                    }
                } else {
                    plugin.logDebug("User '" + user.getNick() + "' mode not okay.");
                    ircBot.asyncIRCMessage(target, plugin.getMsgTemplate(
                            ircBot.botNick, TemplateName.NO_PERM_FOR_IRC_COMMAND)
                            .replace("%NICK%", user.getNick())
                            .replace("%CMDPREFIX%", ircBot.commandPrefix));
                }
            } else {
                if (privateMessage || ircBot.invalidCommandPrivate.get(channelName)) {
                    target = user.getNick();
                }
                plugin.logDebug("Invalid command: " + command);
                String invalidIrcCommand = plugin.getMsgTemplate(
                        ircBot.botNick, TemplateName.INVALID_IRC_COMMAND)
                        .replace("%NICK%", user.getNick())
                        .replace("%CMDPREFIX%", ircBot.commandPrefix);
                if (!invalidIrcCommand.isEmpty()) {
                    if (ircBot.invalidCommandCTCP.get(channelName)) {
                        ircBot.blockingCTCPMessage(target, invalidIrcCommand);
                    } else {
                        ircBot.asyncIRCMessage(target, invalidIrcCommand);
                    }
                }
                if (ircBot.enabledMessages.get(channelName).contains(TemplateName.INVALID_IRC_COMMAND)) {
                    plugin.logDebug("Invalid IRC command dispatched for broadcast...");
                    ircBot.broadcastChat(user, channel, message, false);
                }
            }
        } else {
            if (ircBot.ignoreIRCChat.get(channelName)) {
                plugin.logDebug("Message NOT dispatched for broadcast due to \"ignore-irc-chat\" being true ...");
                return;
            }
            if (privateMessage && !ircBot.relayPrivateChat) {
                plugin.logDebug("Message NOT dispatched for broadcast due to \"relay-private-chat\" being false and this is a private message ...");
                return;
            }
            plugin.logDebug("Message dispatched for broadcast...");
            ircBot.broadcastChat(user, channel, message, false);
        }
    }

    private boolean isValidMode(String modes, User user, Channel channel) {
        boolean modeOkay = false;
        if (modes.equals("*")) {
            return true;
        }
        if (modes.contains("i") && !modeOkay) {
            modeOkay = user.isIrcop();
        }
        if (modes.contains("o") && !modeOkay) {
            modeOkay = user.getChannelsOpIn().contains(channel);
        }
        if (modes.contains("v") && !modeOkay) {
            modeOkay = user.getChannelsVoiceIn().contains(channel);
        }
        if (modes.contains("h") && !modeOkay) {
            modeOkay = user.getChannelsHalfOpIn().contains(channel);
        }
        if (modes.contains("q") && !modeOkay) {
            modeOkay = user.getChannelsOwnerIn().contains(channel);
        }
        if (modes.contains("s") && !modeOkay) {
            modeOkay = user.getChannelsSuperOpIn().contains(channel);
        }
        return modeOkay;
    }

    private void sendMessage(PurpleBot ircBot, String target, String message, IRCMessage.Type responseType) {
        switch (responseType) {
            case CTCP:
                plugin.logDebug("Sending message to target: " + target + " => " + message);
                ircBot.asyncCTCPMessage(target, message);
                break;
            case MESSAGE:
                plugin.logDebug("Sending message to target: " + target + " => " + message);
                ircBot.asyncIRCMessage(target, message);
                break;
            case NOTICE:
                plugin.logDebug("Sending notice to target: " + target + " => " + message);
                ircBot.asyncNoticeMessage(target, message);
                break;
        }
    }

    private String getCommands(CaseInsensitiveMap<CaseInsensitiveMap<CaseInsensitiveMap<String>>> commandMap, String myChannel) {
        if (commandMap.containsKey(myChannel)) {
            List<String> sortedCommands = new ArrayList<>();
            for (String command : commandMap.get(myChannel).keySet()) {
                sortedCommands.add(command);
            }
            Collections.sort(sortedCommands, Collator.getInstance());

            return "Valid commands: " + Joiner.on(", ").join(sortedCommands);
        }
        return "";
    }

}
