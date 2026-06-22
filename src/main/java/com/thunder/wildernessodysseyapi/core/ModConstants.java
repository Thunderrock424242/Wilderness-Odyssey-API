package com.thunder.wildernessodysseyapi.core;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Shared identifiers and release versions used across mod subsystems.
 */
public final class ModConstants {

    private ModConstants() {
    }

    /** NeoForge namespace used for registrations and resource locations. */
    public static final String MOD_ID = "wildernessodysseyapi";

    /** Save-data compatibility version used by world migration checks. */
    public static final String MOD_DEFAULT_WORLD_VERSION = "1.0.0";

    /** Modpack-facing version shown by development diagnostics. */
    public static final String VERSION = "0.0.4";

    /** Shared Log4j logger for runtime mod diagnostics. */
    public static final Logger LOGGER = LogManager.getLogger(MOD_ID);
}
