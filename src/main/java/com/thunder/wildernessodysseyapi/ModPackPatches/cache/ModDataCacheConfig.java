package com.thunder.wildernessodysseyapi.ModPackPatches.cache;

import net.neoforged.neoforge.common.ModConfigSpec;

import java.time.Duration;

/**
 * Configuration toggles for the persistent mod data cache.
 */
public final class ModDataCacheConfig {
    public static final ModConfigSpec CONFIG_SPEC;
    public static final ModConfigSpec.BooleanValue enableCache;
    public static final ModConfigSpec.IntValue maxCacheSizeMb;
    public static final ModConfigSpec.IntValue maxEntryAgeHours;
    public static final ModConfigSpec.BooleanValue verboseLogging;

    static {
        ModConfigSpec.Builder builder = new ModConfigSpec.Builder();
        enableCache = builder.comment("Enable persistent caching for downloaded modpack data")
                .define("enableCache", true);
        maxCacheSizeMb = builder.comment("Maximum size of the mod data cache in megabytes (0 disables the limit)")
                .defineInRange("maxCacheSizeMb", 512, 0, Integer.MAX_VALUE);
        maxEntryAgeHours = builder.comment("Maximum age of cached entries before they are pruned (0 disables age-based pruning)")
                .defineInRange("maxEntryAgeHours", 168, 0, Integer.MAX_VALUE);
        verboseLogging = builder.comment("Enable verbose cache logging for debugging purposes")
                .define("verboseLogging", false);
        CONFIG_SPEC = builder.build();
    }

    private ModDataCacheConfig() {
    }

    /**
     * Returns whether the cache is enabled.
     */
    public static boolean isCacheEnabled() {
        return enableCache.get();
    }

    /**
     * Returns the configured maximum cache size in bytes.
     */
    public static long getMaxCacheSizeBytes() {
        return maxCacheSizeMb.get().longValue() * 1024L * 1024L;
    }

    /**
     * Returns the configured maximum age for cache entries.
     */
    public static Duration getMaxEntryAge() {
        int hours = maxEntryAgeHours.get();
        if (hours <= 0) {
            return Duration.ZERO;
        }
        return Duration.ofHours(hours);
    }

    /**
     * Returns whether verbose logging is enabled.
     */
    public static boolean isVerboseLogging() {
        return verboseLogging.get();
    }
}
