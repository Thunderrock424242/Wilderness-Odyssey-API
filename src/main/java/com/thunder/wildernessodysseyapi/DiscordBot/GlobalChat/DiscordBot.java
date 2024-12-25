package com.thunder.wildernessodysseyapi.DiscordBot.GlobalChat;

import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;

public class DiscordBot extends ListenerAdapter {
    private static JDA jda;
    private static String discordChannelId;

    public static void initializeBot(String token) throws Exception {
        jda = JDABuilder.createDefault(token)
                .addEventListeners(new DiscordBot())
                .build();
        jda.awaitReady();
        System.out.println("Discord bot is ready!");
    }

    public static void setChannelId(String channelId) {
        discordChannelId = channelId;
    }

    public static boolean isInitialized() {
        return jda != null && jda.getStatus() == JDA.Status.CONNECTED;
    }

    public static void shutdown() {
        if (jda != null) {
            jda.shutdown();
        }
    }

    @Override
    public void onMessageReceived(MessageReceivedEvent event) {
        if (event.getAuthor().isBot()) return;

        String channelId = event.getChannel().getId();
        if (channelId.equals(discordChannelId)) {
            String message = event.getAuthor().getName() + ": " + event.getMessage().getContentDisplay();
            MinecraftMessageRelay.sendToMinecraft(message);
        }
    }

    public static void sendMessageToDiscord(String message) {
        if (discordChannelId != null && isInitialized()) {
            jda.getTextChannelById(discordChannelId).sendMessage(message).queue();
        }
    }
}
