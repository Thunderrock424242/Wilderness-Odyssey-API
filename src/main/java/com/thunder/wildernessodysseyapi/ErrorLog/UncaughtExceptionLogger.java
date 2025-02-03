package com.thunder.wildernessodysseyapi.ErrorLog;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Path;

import static com.thunder.wildernessodysseyapi.MainModClass.WildernessOdysseyAPIMainModClass.MOD_ID;

/**
 * Sets up a global uncaught exception handler that logs errors to both the main Neoforge log
 * and a separate file.
 */
public class UncaughtExceptionLogger {
    // This logs to the normal console/latest.log
    private static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    // We'll store the path to our dedicated log file in the logs folder
    private static final Path LOG_FILE_PATH = FMLPaths.LOG_DIR.get().resolve("myapimod-uncaught.log");

    /**
     * Called once during mod setup to install the uncaught exception handler.
     */
    public static void init() {
        Thread.setDefaultUncaughtExceptionHandler((thread, throwable) -> {
            String msg = String.format("Uncaught exception in thread '%s': %s", thread.getName(), throwable);

            // 1) Log to the standard Neoforge logs
            LOGGER.error(msg, throwable);

            // 2) Also write to our dedicated file
            writeToLogFile(msg, throwable);
        });
    }

    /**
     * Writes the uncaught exception info to our custom log file.
     */
    private static void writeToLogFile(String message, Throwable throwable) {
        try (FileWriter fw = new FileWriter(LOG_FILE_PATH.toFile(), true)) {
            fw.write(message + System.lineSeparator());
            if (throwable != null) {
                for (StackTraceElement element : throwable.getStackTrace()) {
                    fw.write("    at " + element.toString() + System.lineSeparator());
                }
                fw.write(System.lineSeparator());
            }
        } catch (IOException e) {
            // If writing fails, at least log that in the main logs
            LOGGER.error("Failed to write to {}", LOG_FILE_PATH, e);
        }
    }
}
