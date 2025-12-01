package com.thunder.wildernessodysseyapi.util;

import com.thunder.wildernessodysseyapi.Core.ModConstants;
import net.minecraft.nbt.NbtIo;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.Locale;

/**
 * Helpers for working around vanilla's NBT parsing safeguards.
 */
public final class NbtParsingUtils {
    private static final int DESIRED_TIMEOUT_MS = 30_000;
    private static boolean attemptedAdjustment = false;

    private NbtParsingUtils() {
    }

    /**
     * Attempts to extend the vanilla NBT parse timeout so large prefab files
     * don't trip the default 10 second limit.
     */
    public static void extendNbtParseTimeout() {
        if (attemptedAdjustment) {
            return;
        }
        attemptedAdjustment = true;

        try {
            for (Field field : NbtIo.class.getDeclaredFields()) {
                if (!Modifier.isStatic(field.getModifiers())) {
                    continue;
                }

                String name = field.getName().toLowerCase(Locale.ROOT);
                if (!name.contains("timeout")) {
                    continue;
                }

                field.setAccessible(true);
                if (field.getType() == int.class) {
                    int previous = field.getInt(null);
                    if (DESIRED_TIMEOUT_MS > previous) {
                        field.setInt(null, DESIRED_TIMEOUT_MS);
                        ModConstants.LOGGER.info("Raised NBT parse timeout from {}ms to {}ms via {}", previous, DESIRED_TIMEOUT_MS,
                                field.getName());
                    }
                } else if (field.getType() == long.class) {
                    long previous = field.getLong(null);
                    if (DESIRED_TIMEOUT_MS > previous) {
                        field.setLong(null, DESIRED_TIMEOUT_MS);
                        ModConstants.LOGGER.info("Raised NBT parse timeout from {}ms to {}ms via {}", previous, DESIRED_TIMEOUT_MS,
                                field.getName());
                    }
                }

                return;
            }
        } catch (Exception e) {
            ModConstants.LOGGER.debug("Failed to adjust NBT parse timeout; continuing with defaults", e);
        }
    }
}
