package com.thunder.wildernessodysseyapi.ModPackPatches.ModConflictChecker.Util;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

import static com.thunder.wildernessodysseyapi.Core.ModConstants.LOGGER;
/**
 * The type Logger util.
 */
public class LoggerUtil {
    private static final String LOG_FILE_PATH = "config/WildernessOdysseyAPI/conflict_log.txt";

    /**
     * Log to console and file by default.
     *
     * @param severity the severity
     * @param message  the message
     */
    public static void log(ConflictSeverity severity, String message) {
        log(severity, message, true);
    }

    /**
     * Logs a message with the given severity. Optionally writes to the console in addition to the
     * dedicated conflict log file.
     *
     * @param severity     the severity of the message
     * @param message      the message to log
     * @param logToConsole whether the message should also be logged to the standard console logger
     */
    public static void log(ConflictSeverity severity, String message, boolean logToConsole) {
        // Add a timestamp to the message
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        String formattedMessage = formatLogMessage(severity, timestamp, message);

        if (logToConsole) {
            switch (severity) {
                case INFO -> LOGGER.info(formattedMessage);
                case WARN -> LOGGER.warn(formattedMessage);
                case ERROR -> LOGGER.error(formattedMessage);
            }
        }

        // Always log to the file
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
