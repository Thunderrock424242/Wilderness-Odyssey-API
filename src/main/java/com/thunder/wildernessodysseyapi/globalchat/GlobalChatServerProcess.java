package com.thunder.wildernessodysseyapi.globalchat;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Launches the external relay server using the same classpath as the running JVM.
 */
public class GlobalChatServerProcess {

    private Process process;

    public void start(int port, String moderationToken, String clusterToken) throws IOException {
        if (process != null && process.isAlive()) {
            return;
        }
        String classpath = System.getProperty("java.class.path");
        List<String> command = new ArrayList<>();
        command.add("java");
        command.add("-Dwilderness.globalchat.token=" + moderationToken);
        command.add("-Dwilderness.globalchat.clustertoken=" + clusterToken);
        passThroughProperty(command, "wilderness.globalchat.discord.botToken");
        passThroughProperty(command, "wilderness.globalchat.discord.pollSeconds");
        passThroughProperty(command, "wilderness.globalchat.discord.channels.help.webhook");
        passThroughProperty(command, "wilderness.globalchat.discord.channels.staff.webhook");
        passThroughProperty(command, "wilderness.globalchat.discord.channels.help.channelId");
        passThroughProperty(command, "wilderness.globalchat.discord.channels.staff.channelId");
        command.add("-cp");
        command.add(classpath);
        command.add("com.thunder.wildernessodysseyapi.globalchat.server.GlobalChatRelayServer");
        command.add(String.valueOf(port));
        ProcessBuilder builder = new ProcessBuilder(command);
        builder.redirectErrorStream(true);
        process = builder.start();
    }

    private void passThroughProperty(List<String> command, String key) {
        String value = System.getProperty(key, "");
        if (value != null && !value.isBlank()) {
            command.add("-D" + key + "=" + value);
        }
    }

    public void stop() {
        if (process != null && process.isAlive()) {
            process.destroy();
        }
    }

    public boolean isRunning() {
        return process != null && process.isAlive();
    }
}
