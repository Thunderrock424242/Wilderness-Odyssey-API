package com.thunder.wildernessodysseyapi.ModConflictChecker.Util;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

import static com.thunder.wildernessodysseyapi.MainModClass.WildernessOdysseyAPIMainModClass.LOGGER;

/**
 * The type Logger util.
 */
public class LoggerUtil {
    private static final String LOG_FILE_PATH = "config/WildernessOdysseyAPI/conflict_log.txt";

    /**
     * Log.
     *
     * @param severity the severity
     * @param message  the message
     */
    public static void log(ConflictSeverity severity, String message) {
        // Add a timestamp to the message
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        String formattedMessage = formatLogMessage(severity, timestamp, message);

        // Log to the console
        switch (severity) {
            case INFO -> LOGGER.info(formattedMessage);
            case WARN -> LOGGER.warn(formattedMessage);
            case ERROR -> LOGGER.error(formattedMessage);
        }

        // Log to the file
        logToFile(formattedMessage);
    }

    private static String formatLogMessage(ConflictSeverity severity, String timestamp, String message) {
        return switch (severity) {
            case INFO -> String.format("[INFO] >>> %s - %s", timestamp, message);
            case WARN -> String.format("[!!! WARNING !!!] >>> %s - %s", timestamp, message);
            case ERROR -> String.format("[!!! ERROR !!!] >>> %s - %s", timestamp, message);
        };
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

    /**
     * The enum Conflict severity.
     */
    public enum ConflictSeverity {
        /**
         * Info conflict severity.
         */
        INFO,
        /**
         * Warn conflict severity.
         */
        WARN,
        /**
         * Error conflict severity.
         */
        ERROR
    }
}
