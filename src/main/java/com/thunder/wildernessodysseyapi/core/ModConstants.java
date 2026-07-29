package com.thunder.wildernessodysseyapi.core;

import net.neoforged.fml.ModList;
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

    /**
     * Compatibility fallback for callers that still reference the historical constant.
     *
     * @deprecated use {@link #currentVersion()} so the value comes from generated NeoForge metadata
     */
    @Deprecated(forRemoval = false)
    public static final String VERSION = "4.1.1";

    /**
     * Returns the release version generated from the top-level {@code build.gradle} project version.
     *
     * <p>NeoForge owns the loaded metadata at runtime, so changelogs, telemetry,
     * and upgrade tracking all observe the same version packaged in the mod JAR.</p>
     */
    public static String currentVersion() {
        return ModList.get().getModContainerById(MOD_ID)
                .map(container -> container.getModInfo().getVersion().toString())
                .orElse(VERSION);
    }

    /** Shared Log4j logger for runtime mod diagnostics. */
    public static final Logger LOGGER = LogManager.getLogger(MOD_ID);
}
