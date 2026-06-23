package com.thunder.wildernessodysseyapi.modlisttracker;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.neoforged.fml.ModList;
import net.neoforged.fml.loading.moddiscovery.ModInfo;

import java.io.IOException;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import static com.thunder.wildernessodysseyapi.core.ModConstants.LOGGER;

/**
 * Matches config files to known mod ids and removes configs owned by mods that are no longer loaded.
 *
 * <p>The cleanup deliberately requires a confident filename or top-level-directory match. Files with
 * unknown or ambiguous ownership remain untouched so a pack-specific config cannot be deleted merely
 * because its filename does not resemble a loaded mod id.</p>
 */
public final class ConfigCleanupService {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Set<String> SUPPORTED_EXTENSIONS = Set.of(
            ".toml", ".json", ".json5", ".yaml", ".yml", ".cfg", ".conf", ".properties", ".ini", ".txt"
    );

    private ConfigCleanupService() {
    }

    /**
     * Scans the config directory without changing any files.
     *
     * @param configDir the active game config directory
     * @return the ownership and stale-config analysis
     */
    public static CleanupResult scan(Path configDir) {
        return execute(configDir, loadedModIds(), ModTracker.getKnownModIds(), false);
    }

    /**
     * Deletes configs that confidently map to a historically known but currently absent mod.
     *
     * @param configDir the active game config directory
     * @return the analysis plus successful and failed deletions
     */
    public static CleanupResult clean(Path configDir) {
        return execute(configDir, loadedModIds(), ModTracker.getKnownModIds(), true);
    }

    /**
     * Writes a machine-readable cleanup report for pack maintainers to inspect after the command runs.
     *
     * @param reportPath destination JSON path
     * @param result cleanup result to serialize
     */
    public static void writeReport(Path reportPath, CleanupResult result) {
        try {
            Files.createDirectories(reportPath.getParent());
            try (Writer writer = Files.newBufferedWriter(reportPath, StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE)) {
                GSON.toJson(result, writer);
            }
        } catch (IOException e) {
            LOGGER.error("[ConfigCleanup] Failed writing report to {}", reportPath, e);
        }
    }

    // The injectable mod-id sets keep ownership and deletion behavior unit-testable without booting NeoForge.
    static CleanupResult execute(Path configDir, Collection<String> installedIds,
                                 Collection<String> historicallyKnownIds, boolean delete) {
        Path normalizedConfigDir = configDir.toAbsolutePath().normalize();
        Set<String> installed = normalizeModIds(installedIds);
        Set<String> known = normalizeModIds(historicallyKnownIds);
        known.addAll(installed);

        List<String> absent = known.stream()
                .filter(modId -> !installed.contains(modId))
                .sorted()
                .toList();

        Map<String, String> active = new LinkedHashMap<>();
        Map<String, String> stale = new LinkedHashMap<>();
        Map<String, List<String>> ambiguous = new LinkedHashMap<>();
        List<String> unresolved = new ArrayList<>();
        Map<String, Path> pathsByRelativeName = collectConfigFiles(normalizedConfigDir);

        // Classify everything before deleting so the report reflects one consistent filesystem snapshot.
        for (Map.Entry<String, Path> entry : pathsByRelativeName.entrySet()) {
            String relativePath = entry.getKey();
            List<String> matches = findConfidentMatches(relativePath, known);
            if (matches.isEmpty()) {
                unresolved.add(relativePath);
            } else if (matches.size() > 1) {
                ambiguous.put(relativePath, matches);
            } else {
                String owner = matches.getFirst();
                if (installed.contains(owner)) {
                    active.put(relativePath, owner);
                } else {
                    stale.put(relativePath, owner);
                }
            }
        }

        List<String> deleted = new ArrayList<>();
        Map<String, String> failed = new LinkedHashMap<>();
        if (delete) {
            for (String relativePath : stale.keySet()) {
                deleteConfigFile(normalizedConfigDir, pathsByRelativeName.get(relativePath), relativePath, deleted, failed);
            }
        }

        CleanupResult result = new CleanupResult(
                Instant.now().toString(),
                normalizedConfigDir.toString(),
                delete,
                installed.size(),
                known.size(),
                pathsByRelativeName.size(),
                absent,
                active,
                stale,
                ambiguous,
                unresolved,
                deleted,
                failed
        );
        LOGGER.info("[ConfigCleanup] Complete. delete={}, total={}, active={}, stale={}, ambiguous={}, unresolved={}, deleted={}, failed={}",
                delete, pathsByRelativeName.size(), active.size(), stale.size(), ambiguous.size(), unresolved.size(),
                deleted.size(), failed.size());
        return result;
    }

    private static Set<String> loadedModIds() {
        Set<String> result = new LinkedHashSet<>();
        ModList.get().getMods().stream()
                .filter(mod -> mod instanceof ModInfo)
                .map(mod -> ((ModInfo) mod).getModId())
                .forEach(result::add);
        return result;
    }

    private static Set<String> normalizeModIds(Collection<String> modIds) {
        Set<String> normalized = new LinkedHashSet<>();
        if (modIds == null) {
            return normalized;
        }
        for (String modId : modIds) {
            if (modId != null && !modId.isBlank()) {
                normalized.add(modId.toLowerCase(Locale.ROOT));
            }
        }
        return normalized;
    }

    private static Map<String, Path> collectConfigFiles(Path configDir) {
        Map<String, Path> files = new LinkedHashMap<>();
        if (!Files.isDirectory(configDir, LinkOption.NOFOLLOW_LINKS)) {
            return files;
        }

        try (var stream = Files.walk(configDir)) {
            stream.filter(path -> Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS))
                    .filter(path -> hasSupportedExtension(path.getFileName().toString()))
                    .sorted(Comparator.naturalOrder())
                    .forEach(path -> files.put(toRelativeName(configDir, path), path));
        } catch (IOException e) {
            LOGGER.error("[ConfigCleanup] Failed to scan config directory {}", configDir, e);
        }
        return files;
    }

    private static String toRelativeName(Path configDir, Path path) {
        return configDir.relativize(path).toString().replace('\\', '/');
    }

    private static boolean hasSupportedExtension(String filename) {
        String lower = filename.toLowerCase(Locale.ROOT);
        return SUPPORTED_EXTENSIONS.stream().anyMatch(lower::endsWith);
    }

    private static List<String> findConfidentMatches(String relativePath, Set<String> knownModIds) {
        String lowerPath = relativePath.toLowerCase(Locale.ROOT);
        int slashIndex = lowerPath.indexOf('/');
        String topLevelDirectory = slashIndex >= 0 ? lowerPath.substring(0, slashIndex) : "";
        String filename = lowerPath.substring(lowerPath.lastIndexOf('/') + 1);
        String baseName = filename.contains(".") ? filename.substring(0, filename.lastIndexOf('.')) : filename;
        Set<String> matches = new LinkedHashSet<>();

        for (String modId : knownModIds) {
            if (topLevelDirectory.equals(modId)
                    || baseName.equals(modId)
                    || baseName.startsWith(modId + "-")
                    || baseName.startsWith(modId + "_")
                    || baseName.startsWith(modId + ".")) {
                matches.add(modId);
            }
        }
        return matches.stream().sorted().toList();
    }

    private static void deleteConfigFile(Path configDir, Path file, String relativePath,
                                         List<String> deleted, Map<String, String> failed) {
        if (file == null || !file.toAbsolutePath().normalize().startsWith(configDir)) {
            failed.put(relativePath, "Path escaped the config directory safety boundary");
            return;
        }

        try {
            if (Files.deleteIfExists(file)) {
                deleted.add(relativePath);
                try {
                    deleteEmptyParents(configDir, file.getParent());
                } catch (IOException e) {
                    // Empty-directory pruning is cosmetic; the requested config file was already deleted.
                    LOGGER.debug("[ConfigCleanup] Could not prune an empty parent directory for {}", file, e);
                }
            }
        } catch (IOException | SecurityException e) {
            failed.put(relativePath, e.getClass().getSimpleName() + ": " + String.valueOf(e.getMessage()));
            LOGGER.error("[ConfigCleanup] Failed deleting stale config {}", file, e);
        }
    }

    private static void deleteEmptyParents(Path configDir, Path directory) throws IOException {
        Path current = directory;
        while (current != null && !current.equals(configDir) && current.startsWith(configDir)) {
            try (var children = Files.list(current)) {
                if (children.findAny().isPresent()) {
                    return;
                }
            }
            Files.deleteIfExists(current);
            current = current.getParent();
        }
    }

    /**
     * Immutable report describing active ownership, stale candidates, skipped files, and deletion outcomes.
     */
    public record CleanupResult(String generatedAt,
                                String configDirectory,
                                boolean deletionRequested,
                                int installedModCount,
                                int knownModCount,
                                int totalConfigFiles,
                                List<String> absentModIds,
                                Map<String, String> activeConfigs,
                                Map<String, String> staleConfigCandidates,
                                Map<String, List<String>> ambiguousConfigs,
                                List<String> unresolvedConfigs,
                                List<String> deletedConfigs,
                                Map<String, String> failedDeletions) {
    }
}
