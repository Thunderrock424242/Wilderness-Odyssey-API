package com.thunder.wildernessodysseyapi.ModConflictChecker.Util;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

import static com.thunder.wildernessodysseyapi.MainModClass.WildernessOdysseyAPIMainModClass.LOGGER;

public class LoggerUtil {
    private static final String LOG_FILE_PATH = "config/WildernessOdysseyAPI/conflict_log.txt";

    public static void log(ConflictSeverity severity, String message) {
        // Add a timestamp to the message
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        String formattedMessage = "[" + timestamp + "] [" + severity.name() + "] " + message;

        // Log to the console
        switch (severity) {
            case INFO -> LOGGER.info(formattedMessage);
            case WARN -> LOGGER.warn(formattedMessage);
            case ERROR -> LOGGER.error(formattedMessage);
        }

        // Log to the file
        logToFile(formattedMessage);
    }

    private static void logToFile(String message) {
        File logFile = new File(LOG_FILE_PATH);
        try {
            // Ensure parent directory exists
            File parentDir = logFile.getParentFile();
            if (!parentDir.exists() && !parentDir.mkdirs()) {
                LOGGER.error("Failed to create log directory: {}", parentDir.getAbsolutePath());
                return;
            }

            // Write the message to the file
            try (BufferedWriter writer = new BufferedWriter(new FileWriter(logFile, true))) {
                writer.write(message);
                writer.newLine();
            }
        } catch (IOException e) {
            LOGGER.error("Failed to write to conflict log file: {}", e.getMessage());
        }
    }

    public enum ConflictSeverity {
        INFO,
        WARN,
        ERROR
    }
}
