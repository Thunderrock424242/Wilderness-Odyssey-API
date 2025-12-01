package com.thunder.wildernessodysseyapi.globalchat;

import java.io.IOException;

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
        ProcessBuilder builder = new ProcessBuilder("java",
                "-Dwilderness.globalchat.token=" + moderationToken,
                "-Dwilderness.globalchat.clustertoken=" + clusterToken,
                "-cp", classpath,
                "com.thunder.wildernessodysseyapi.globalchat.server.GlobalChatRelayServer",
                String.valueOf(port));
        builder.redirectErrorStream(true);
        process = builder.start();
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
