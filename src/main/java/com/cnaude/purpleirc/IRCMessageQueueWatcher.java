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

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import net.md_5.bungee.api.scheduler.ScheduledTask;

/**
 *
 * @author Chris Naude Poll the command queue and dispatch to Bukkit
 */
public class IRCMessageQueueWatcher {

    private final PurpleIRC plugin;
    private final PurpleBot ircBot;
    private final ScheduledTask bt;
    private final BlockingQueue<IRCMessage> queue = new LinkedBlockingQueue<>();
    private final String REGEX_CLEAN = "^[\\r\\n]|[\\r\\n]$";
    private final String REGEX_CRLF = "\\r\\n";
    private final String LF = "\\n";

    /**
     *
     * @param plugin
     * @param ircBot
     */
    public IRCMessageQueueWatcher(final PurpleBot ircBot, final PurpleIRC plugin) {
        this.plugin = plugin;
        this.ircBot = ircBot;
        bt = this.plugin.getProxy().getScheduler().schedule(this.plugin, new Runnable() {
            @Override
            public void run() {
                queueAndSend();
            }
        }, 0, 1000, TimeUnit.MILLISECONDS);
    }

    private void queueAndSend() {
        IRCMessage ircMessage = queue.poll();
        if (ircMessage != null) {
            plugin.logDebug("[" + queue.size() + "]: queueAndSend message detected");
            for (String s : cleanupAndSplitMessage(ircMessage.message)) {
                switch (ircMessage.type) {
                    case MESSAGE:
                        blockingIRCMessage(ircMessage.target, s);
                        break;
                    case CTCP:
                        blockingCTCPMessage(ircMessage.target, s);
                        break;
                    case NOTICE:
                        blockingNoticeMessage(ircMessage.target, s);
                }
            }
        }
    }

    private void blockingIRCMessage(final String target, final String message) {
        if (!ircBot.isConnected()) {
            return;
        }
        plugin.logDebug("[blockingIRCMessage] About to send IRC message to " + target + ": " + message);
        ircBot.bot.sendIRC().message(target, message);
        plugin.logDebug("[blockingIRCMessage] Message sent to " + target + ": " + message);
    }

    private void blockingCTCPMessage(final String target, final String message) {
        if (!ircBot.isConnected()) {
            return;
        }
        plugin.logDebug("[blockingCTCPMessage] About to send IRC message to " + target + ": " + message);
        ircBot.bot.sendIRC().ctcpResponse(target, message);
        plugin.logDebug("[blockingCTCPMessage] Message sent to " + target + ": " + message);
    }
    
    
    private void blockingNoticeMessage(final String target, final String message) {
        if (!ircBot.isConnected()) {
            return;
        }
        plugin.logDebug("[blockingNoticeMessage] About to send IRC notice to " + target + ": " + message);
        ircBot.bot.sendIRC().notice(target, message);
        plugin.logDebug("[blockingNoticeMessage] Notice sent to " + target + ": " + message);
    }

    private String[] cleanupAndSplitMessage(String message) {
        return message.replaceAll(REGEX_CLEAN, "").replaceAll(REGEX_CRLF, "\n").split(LF);
    }

    public void cancel() {
        bt.cancel();
    }

    public String clearQueue() {
        int size = queue.size();
        if (!queue.isEmpty()) {
            queue.clear();
        }
        return "Elements removed from message queue: " + size;
    }

    /**
     *
     * @param ircMessage
     */
    public void add(IRCMessage ircMessage) {
        queue.offer(ircMessage);
    }

}
