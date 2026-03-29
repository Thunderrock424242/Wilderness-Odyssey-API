package com.thunder.wildernessodysseyapi.ModPackPatches.ModListTracker;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.neoforged.fml.ModList;
import net.neoforged.fml.loading.moddiscovery.ModInfo;

import java.io.IOException;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static com.thunder.wildernessodysseyapi.core.ModConstants.LOGGER;
import static com.thunder.wildernessodysseyapi.core.ModConstants.MOD_ID;

/**
 * Audits files under the config directory and attempts to link each file to an installed mod id.
 */
public final class ConfigLinkAudit {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Set<String> SUPPORTED_EXTENSIONS = Set.of(
            ".toml", ".json", ".json5", ".yaml", ".yml", ".cfg", ".conf", ".properties", ".ini", ".txt"
    );

    private ConfigLinkAudit() {
    }

    public static AuditResult run(Path configDir, Path reportPath, Path unresolvedLogPath) {
        List<String> installedMods = ModList.get().getMods().stream()
                .filter(mod -> mod instanceof ModInfo)
                .map(mod -> ((ModInfo) mod).getModId().toLowerCase(Locale.ROOT))
                .distinct()
                .sorted()
                .toList();

        List<String> configFiles = collectConfigFiles(configDir);
        Map<String, String> linked = new LinkedHashMap<>();
        Map<String, List<String>> ambiguous = new LinkedHashMap<>();
        List<String> unresolved = new ArrayList<>();

        for (String relPath : configFiles) {
            List<String> matches = guessModMatches(relPath, installedMods);
            if (matches.size() == 1) {
                linked.put(relPath, matches.get(0));
            } else if (matches.isEmpty()) {
                unresolved.add(relPath);
            } else {
                ambiguous.put(relPath, matches);
            }
        }

        AuditResult result = new AuditResult(
                Instant.now().toString(),
                configDir.toString(),
                installedMods.size(),
                configFiles.size(),
                linked,
                ambiguous,
                unresolved
        );

        writeReport(reportPath, result);
        writeUnresolvedLog(unresolvedLogPath, unresolved, ambiguous);
        unresolved.forEach(file -> LOGGER.error("[ConfigLinkAudit] Unresolved config file: {}", file));
        ambiguous.forEach((file, mods) -> LOGGER.error("[ConfigLinkAudit] Ambiguous config file {} -> {}", file, mods));
        LOGGER.info("[ConfigLinkAudit] Audit complete. Total={}, linked={}, ambiguous={}, unresolved={}",
                configFiles.size(), linked.size(), ambiguous.size(), unresolved.size());

        return result;
    }

    private static List<String> collectConfigFiles(Path configDir) {
        if (!Files.exists(configDir)) {
            return Collections.emptyList();
        }

        try (var stream = Files.walk(configDir)) {
            return stream
                    .filter(Files::isRegularFile)
                    .filter(path -> hasSupportedExtension(path.getFileName().toString()))
                    .map(path -> configDir.relativize(path).toString().replace('\\', '/'))
                    .sorted(Comparator.naturalOrder())
                    .toList();
        } catch (IOException e) {
            LOGGER.error("[ConfigLinkAudit] Failed to scan config directory {}", configDir, e);
            return Collections.emptyList();
        }
    }

    private static boolean hasSupportedExtension(String filename) {
        String lower = filename.toLowerCase(Locale.ROOT);
        for (String ext : SUPPORTED_EXTENSIONS) {
            if (lower.endsWith(ext)) {
                return true;
            }
        }
        return false;
    }

    private static List<String> guessModMatches(String relativePath, List<String> modIds) {
        String lowerPath = relativePath.toLowerCase(Locale.ROOT);
        String filename = lowerPath.substring(lowerPath.lastIndexOf('/') + 1);
        String base = filename.contains(".") ? filename.substring(0, filename.lastIndexOf('.')) : filename;

        Set<String> matches = new LinkedHashSet<>();

        for (String modId : modIds) {
            if (lowerPath.startsWith(modId + "/")
                    || filename.equals(modId)
                    || filename.startsWith(modId + "-")
                    || filename.startsWith(modId + "_")
                    || filename.startsWith(modId + ".")
                    || base.equals(modId)
                    || base.contains(modId)) {
                matches.add(modId);
            }
        }

        if (matches.isEmpty()) {
            Set<String> tokens = tokenize(base);
            for (String modId : modIds) {
                if (tokens.contains(modId)) {
                    matches.add(modId);
                }
            }
        }

        if (matches.contains(MOD_ID) && matches.size() > 1) {
            matches.remove(MOD_ID);
        }

        return matches.stream().sorted().collect(Collectors.toList());
    }

    private static Set<String> tokenize(String input) {
        Set<String> tokens = new HashSet<>();
        for (String token : input.split("[^a-z0-9]+")) {
            if (!token.isBlank()) {
                tokens.add(token);
            }
        }
        return tokens;
    }

    private static void writeReport(Path reportPath, AuditResult result) {
        try {
            Files.createDirectories(reportPath.getParent());
            try (Writer writer = Files.newBufferedWriter(reportPath, StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE)) {
                GSON.toJson(result, writer);
            }
        } catch (IOException e) {
            LOGGER.error("[ConfigLinkAudit] Failed writing report to {}", reportPath, e);
        }
    }

    private static void writeUnresolvedLog(Path logPath, List<String> unresolved, Map<String, List<String>> ambiguous) {
        List<String> lines = new ArrayList<>();
        lines.add("[ConfigLinkAudit] Unresolved and ambiguous config files");
        lines.add("Timestamp: " + Instant.now());
        lines.add("");

        if (unresolved.isEmpty()) {
            lines.add("No unresolved files.");
        } else {
            lines.add("Unresolved files:");
            unresolved.forEach(file -> lines.add("- " + file));
        }

        lines.add("");
        if (ambiguous.isEmpty()) {
            lines.add("No ambiguous files.");
        } else {
            lines.add("Ambiguous files:");
            ambiguous.forEach((file, mods) -> lines.add("- " + file + " -> " + String.join(", ", mods)));
        }

        try {
            Files.createDirectories(logPath.getParent());
            Files.write(logPath, lines, StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE);
        } catch (IOException e) {
            LOGGER.error("[ConfigLinkAudit] Failed writing unresolved log {}", logPath, e);
        }
    }

    public record AuditResult(String generatedAt,
                              String configDirectory,
                              int installedModCount,
                              int totalConfigFiles,
                              Map<String, String> linkedConfigs,
                              Map<String, List<String>> ambiguousConfigs,
                              List<String> unresolvedConfigs) {
    }
}
