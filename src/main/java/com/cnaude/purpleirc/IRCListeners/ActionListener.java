/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package com.cnaude.purpleirc.IRCListeners;

import com.cnaude.purpleirc.PurpleBot;
import com.cnaude.purpleirc.PurpleIRC;
import org.pircbotx.Channel;
import org.pircbotx.hooks.ListenerAdapter;
import org.pircbotx.hooks.events.ActionEvent;

/**
 *
 * @author cnaude
 */
public class ActionListener extends ListenerAdapter {

    PurpleIRC plugin;
    PurpleBot ircBot;

    /**
     *
     * @param plugin
     * @param ircBot
     */
    public ActionListener(PurpleIRC plugin, PurpleBot ircBot) {
        this.plugin = plugin;
        this.ircBot = ircBot;
    }

    /**
     *
     * @param event
     */
    @Override
    public void onAction(ActionEvent event) {
        Channel channel = event.getChannel();
        if (ircBot.isValidChannel(channel.getName())) {         
            ircBot.broadcastAction(event.getUser(), channel, event.getAction());
        }
    }
}
