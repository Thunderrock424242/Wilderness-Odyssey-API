package com.thunder.wildernessodysseyapi.DiscordBot.GlobalChat;

import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;

public class DiscordBotListener extends ListenerAdapter {
    @Override
    public void onMessageReceived(MessageReceivedEvent event) {
        Message message = event.getMessage();
        String content = message.getContentRaw();
        String channelName = event.getChannel().getName();

        // Log the message
        System.out.println("Message from Discord: " + content + " in channel: " + channelName);

        // Forward to webhook
        DiscordMessageHandler.onDiscordMessageSend(content, channelName);
    }
}
