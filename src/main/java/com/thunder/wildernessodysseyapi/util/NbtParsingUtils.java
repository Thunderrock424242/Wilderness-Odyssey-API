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
    public static synchronized void extendNbtParseTimeout() {
        if (attemptedAdjustment) {
            return;
        }

        try {
            Field target = null;
            for (Field field : NbtIo.class.getDeclaredFields()) {
                if (!Modifier.isStatic(field.getModifiers())) {
                    continue;
                }

                String name = field.getName().toLowerCase(Locale.ROOT);
                if (name.contains("timeout") || name.contains("time_limit") || name.contains("timeconstraint")) {
                    target = field;
                    break;
                }
            }

            if (target == null) {
                for (Field field : NbtIo.class.getDeclaredFields()) {
                    if (!Modifier.isStatic(field.getModifiers())) {
                        continue;
                    }
                    if (field.getType() != int.class && field.getType() != long.class) {
                        continue;
                    }

                    field.setAccessible(true);
                    long value = field.getType() == int.class ? field.getInt(null) : field.getLong(null);
                    if (value >= 5_000 && value <= DESIRED_TIMEOUT_MS) {
                        target = field;
                        break;
                    }
                }
            }

            if (target == null) {
                ModConstants.LOGGER.debug("No timeout-like field found on NbtIo; leaving vanilla parse timeout intact");
                return;
            }

            target.setAccessible(true);
            if (target.getType() == int.class) {
                int previous = target.getInt(null);
                if (DESIRED_TIMEOUT_MS > previous) {
                    target.setInt(null, DESIRED_TIMEOUT_MS);
                    ModConstants.LOGGER.info("Raised NBT parse timeout from {}ms to {}ms via {}", previous, DESIRED_TIMEOUT_MS,
                            target.getName());
                }
            } else if (target.getType() == long.class) {
                long previous = target.getLong(null);
                if (DESIRED_TIMEOUT_MS > previous) {
                    target.setLong(null, DESIRED_TIMEOUT_MS);
                    ModConstants.LOGGER.info("Raised NBT parse timeout from {}ms to {}ms via {}", previous, DESIRED_TIMEOUT_MS,
                            target.getName());
                }
            }

            attemptedAdjustment = true;
        } catch (Exception e) {
            ModConstants.LOGGER.debug("Failed to adjust NBT parse timeout; continuing with defaults", e);
        }
    }
}
