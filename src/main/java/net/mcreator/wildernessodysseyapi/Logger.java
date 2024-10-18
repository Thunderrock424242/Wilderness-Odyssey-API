/*
 * The code of this mod element is always locked.
 *
 * You can register new events in this class too.
 *
 * If you want to make a plain independent class, create it using
 * Project Browser -> New... and make sure to make the class
 * outside net.mcreator.wildernessoddesyapi as this package is managed by MCreator.
 *
 * If you change workspace package, modid or prefix, you will need
 * to manually adapt this file to these changes or remake it.
 *
 * This class will be added in the mod root package.
*/
package net.mcreator.wildernessodysseyapi;

import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class Logger {

    private static final String GITHUB_REPO_OWNER = "your-github-username";
    private static final String GITHUB_REPO_NAME = "anti-cheat-global-logs";
    private static final String GITHUB_TOKEN = "your-personal-access-token";

    private static final boolean ENABLE_GLOBAL_LOGGING = true;
    private static final String LOCAL_LOG_PATH = "logs/anticheat-logs.txt";
    private static final String SERVER_ID = "server-unique-id";

    private static final List<String> logQueue = new LinkedList<>();
    private static final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);

    static {
        scheduler.scheduleAtFixedRate(Logger::batchProcessLogs, 5, 5, TimeUnit.MINUTES);
    }

    public static void logFlaggedPlayer(String playerName, String action) {
        String logEntry = "ServerID: " + SERVER_ID + " | Player: " + playerName + " - Action: " + action + "\n";
        synchronized (logQueue) {
            logQueue.add(logEntry);
        }
        logToLocalFile(logEntry);
    }

    private static void logToLocalFile(String logEntry) {
        File logFile = new File(LOCAL_LOG_PATH);

        try {
            logFile.getParentFile().mkdirs();
            try (FileWriter writer = new FileWriter(logFile, true)) {
                writer.write(logEntry);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static void batchProcessLogs() {
        if (!ENABLE_GLOBAL_LOGGING) {
            return;
        }

        StringBuilder batchedLogs = new StringBuilder();
        synchronized (logQueue) {
            if (logQueue.isEmpty()) {
                return;
            }

            for (String log : logQueue) {
                batchedLogs.append(log);
            }
            logQueue.clear();
        }

        sendBatchedLogsToGitHub(batchedLogs.toString());
    }

    private static void sendBatchedLogsToGitHub(@NotNull String batchedLog) {
        try {
            String apiUrl = "https://api.github.com/repos/" + GITHUB_REPO_OWNER + "/" + GITHUB_REPO_NAME + "/contents/global-anticheat-logs.txt";
            String message = "Updating global anti-cheat logs with batch update";
            String encodedContent = Base64.getEncoder().encodeToString(batchedLog.getBytes(StandardCharsets.UTF_8));

            String jsonBody = "{"
                    + "\"message\": \"" + message + "\","
                    + "\"content\": \"" + encodedContent + "\""
                    + "}";

            URL url = new URL(apiUrl);
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("PUT");
            connection.setRequestProperty("Authorization", "token " + GITHUB_TOKEN);
            connection.setRequestProperty("Accept", "application/vnd.github.v3+json");
            connection.setDoOutput(true);

            connection.getOutputStream().write(jsonBody.getBytes(StandardCharsets.UTF_8));

            int responseCode = connection.getResponseCode();
            if (responseCode != HttpURLConnection.HTTP_CREATED && responseCode != HttpURLConnection.HTTP_OK) {
                System.err.println("Failed to log to GitHub. Response Code: " + responseCode);
            }

        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
