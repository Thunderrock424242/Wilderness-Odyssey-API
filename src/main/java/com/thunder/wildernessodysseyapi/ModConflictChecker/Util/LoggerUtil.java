package com.thunder.wildernessodysseyapi.ModConflictChecker.Util;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

import static com.thunder.wildernessodysseyapi.Core.ModConstants.LOGGER;

import net.minecraft.network.chat.Component;
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
            case INFO -> Component.translatable("log.wildernessodysseyapi.conflict_info", timestamp, message).getString();
            case WARN -> Component.translatable("log.wildernessodysseyapi.conflict_warn", timestamp, message).getString();
            case ERROR -> Component.translatable("log.wildernessodysseyapi.conflict_error", timestamp, message).getString();
        };
    }

    private static void logToFile(String message) {
        File logFile = new File(LOG_FILE_PATH);
        try {
            // Ensure parent directory exists
            File parentDir = logFile.getParentFile();
            if (!parentDir.exists() && !parentDir.mkdirs()) {
                LOGGER.error(Component.translatable("log.wildernessodysseyapi.failed_create_log_dir", parentDir.getAbsolutePath()).getString());
                return;
            }

            // Write the message to the file
            try (BufferedWriter writer = new BufferedWriter(new FileWriter(logFile, true))) {
                writer.write(message);
                writer.newLine();
            }
        } catch (IOException e) {
            LOGGER.error(Component.translatable("log.wildernessodysseyapi.failed_write_conflict_log", e.getMessage()).getString());
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
