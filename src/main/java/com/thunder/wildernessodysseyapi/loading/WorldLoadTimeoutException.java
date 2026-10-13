package com.thunder.wildernessodysseyapi.loading;

/** Identifies our cooperative startup abort without masking unrelated server crashes. */
public final class WorldLoadTimeoutException extends RuntimeException {
    public WorldLoadTimeoutException() {
        super("Failed to load world after 10 minutes. Try creating a new world, or contact the modpack developers and submit an issue report.");
    }
}
