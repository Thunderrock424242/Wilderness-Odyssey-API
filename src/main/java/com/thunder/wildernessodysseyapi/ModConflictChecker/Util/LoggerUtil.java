package com.thunder.wildernessodysseyapi.ModConflictChecker.Util;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;

import static com.thunder.wildernessodysseyapi.MainModClass.WildernessOdysseyAPIMainModClass.LOGGER;
import static org.apache.logging.log4j.Level.*;

public class LoggerUtil {
    private static final String LOG_FILE_PATH = "config/WildernessOdysseyAPI/conflict_log.txt";

    public static void log(ConflictSeverity severity, String message) {
        // Log to the console based on severity
        switch (severity) {
            case INFO -> LOGGER.info(message);
            case WARN -> LOGGER.warn(message);
            case ERROR -> LOGGER.error(message);
        }

        // Log to the file
        logToFile("[" + severity.name() + "] " + message);
    }

    private static void logToFile(String message) {
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(LOG_FILE_PATH, true))) {
            writer.write(message);
            writer.newLine();
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