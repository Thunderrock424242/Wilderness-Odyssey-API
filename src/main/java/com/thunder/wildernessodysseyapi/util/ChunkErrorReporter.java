package com.thunder.wildernessodysseyapi.util;

import com.thunder.wildernessodysseyapi.core.ModConstants;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.neoforged.fml.ModList;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Centralized logging helper for chunk generation/upgrade failures.
 */
public final class ChunkErrorReporter {
    private static final Set<String> FRAMEWORK_PACKAGES = Set.of(
            "java.", "javax.", "jdk.", "sun.", "com.sun.",
            "net.minecraft.", "com.mojang.", "org.spongepowered.", "net.neoforged.",
            "com.thunder.wildernessodysseyapi."
    );

    private ChunkErrorReporter() {
    }

    public static void reportChunkError(String operation,
                                        ServerLevel level,
                                        ChunkPos chunkPos,
                                        Throwable throwable) {
        ResourceLocation dimension = level.dimension().location();
        String suspectedMods = formatSuspectedMods(throwable);
        Throwable rootCause = rootCause(throwable);

        ModConstants.LOGGER.error(
                "Chunk {} failed in [{}] at chunk {} (block {}). Suspected mods: {}. Root cause: {}: {}",
                operation,
                dimension,
                chunkPos,
                chunkPos.getWorldPosition(),
                suspectedMods,
                rootCause.getClass().getName(),
                rootCause.getMessage(),
                throwable
        );
    }

    private static String formatSuspectedMods(Throwable throwable) {
        Set<String> knownMods = ModList.get().getMods().stream()
                .map(info -> info.getModId().toLowerCase(Locale.ROOT))
                .collect(java.util.stream.Collectors.toUnmodifiableSet());

        Set<String> matches = new LinkedHashSet<>();
        Throwable cursor = throwable;
        while (cursor != null && matches.size() < 6) {
            for (StackTraceElement frame : cursor.getStackTrace()) {
                String className = frame.getClassName();
                if (isFrameworkClass(className)) {
                    continue;
                }

                String lowerName = className.toLowerCase(Locale.ROOT);
                for (String modId : knownMods) {
                    if (matches.size() >= 6) {
                        break;
                    }
                    if (containsModHint(lowerName, modId)) {
                        matches.add(modId);
                    }
                }

                if (matches.size() >= 6) {
                    break;
                }
            }
            cursor = cursor.getCause();
            if (cursor == throwable) {
                break;
            }
        }

        if (matches.isEmpty()) {
            return "unknown";
        }

        List<String> sorted = new ArrayList<>(matches);
        sorted.sort(Comparator.naturalOrder());
        return String.join(", ", sorted);
    }

    private static boolean containsModHint(String className, String modId) {
        String normalized = modId.replace('-', '_');
        return className.contains('.' + modId + '.')
                || className.contains('.' + normalized + '.')
                || className.contains("/" + modId + "/")
                || className.endsWith('.' + modId)
                || className.endsWith('.' + normalized);
    }

    private static boolean isFrameworkClass(String className) {
        for (String prefix : FRAMEWORK_PACKAGES) {
            if (className.startsWith(prefix)) {
                return true;
            }
        }
        return false;
    }

    private static Throwable rootCause(Throwable throwable) {
        Throwable current = throwable;
        while (current.getCause() != null && current.getCause() != current) {
            current = current.getCause();
        }
        return current;
    }
}
