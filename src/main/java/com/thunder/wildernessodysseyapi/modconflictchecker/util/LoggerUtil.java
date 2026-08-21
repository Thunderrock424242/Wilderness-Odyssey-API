package com.thunder.wildernessodysseyapi.modconflictchecker.util;

import static com.thunder.wildernessodysseyapi.core.ModConstants.LOGGER;

/**
 * Routes compatibility diagnostics through the normal mod logger.
 *
 * <p>Older versions appended every registry entry and watchdog heartbeat to a separate unbounded
 * file. Normal log rotation now owns retention, and callers are expected to report summaries or
 * actionable anomalies only.</p>
 */
public final class LoggerUtil {

    private LoggerUtil() {
        // Utility class
    }

    /** Logs one actionable compatibility diagnostic through the rotating mod log. */
    public static void log(ConflictSeverity severity, String message) {
        log(severity, message, true);
    }

    /**
     * Compatibility overload retained for older callers that selected the former dedicated sink.
     *
     * @param severity     the severity of the message
     * @param message      the message to log
     * @param ignoredLogToConsole ignored; diagnostics now always use the normal rotating mod log
     * @deprecated the separate unbounded conflict-log sink was removed
     */
    @Deprecated(forRemoval = false)
    public static void log(ConflictSeverity severity, String message, boolean ignoredLogToConsole) {
        switch (severity) {
            case INFO -> LOGGER.info("[Compatibility diagnostics] {}", message);
            case WARN -> LOGGER.warn("[Compatibility diagnostics] {}", message);
            case ERROR -> LOGGER.error("[Compatibility diagnostics] {}", message);
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
