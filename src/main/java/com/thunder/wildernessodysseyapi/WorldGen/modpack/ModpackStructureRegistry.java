package com.thunder.wildernessodysseyapi.WorldGen.modpack;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonSyntaxException;
import com.thunder.wildernessodysseyapi.Core.ModConstants;
import com.thunder.wildernessodysseyapi.WorldGen.structure.NBTStructurePlacer;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.fml.loading.FMLPaths;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * Loads user-provided NBT templates from config/wildernessodysseyapi/modpack_structures
 * and exposes runtime placers for commands/integration hooks.
 */
public final class ModpackStructureRegistry {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path ROOT = FMLPaths.CONFIGDIR.get()
            .resolve(ModConstants.MOD_ID)
            .resolve("modpack_structures");

    private static final Map<ResourceLocation, Entry> ENTRIES = new LinkedHashMap<>();

    private ModpackStructureRegistry() {
    }

    public static synchronized void loadAll() {
        ENTRIES.clear();
        try {
            Files.createDirectories(ROOT);
            writeTemplateConfigIfMissing();
        } catch (IOException e) {
            ModConstants.LOGGER.error("Failed to initialize modpack structure directory {}", ROOT, e);
            return;
        }

        List<Path> nbtFiles = new ArrayList<>();
        try (var stream = Files.list(ROOT)) {
            stream.filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".nbt"))
                    .sorted(Comparator.comparing(path -> path.getFileName().toString()))
                    .forEach(nbtFiles::add);
        } catch (IOException e) {
            ModConstants.LOGGER.error("Failed to scan modpack structure directory {}", ROOT, e);
            return;
        }

        for (Path nbtPath : nbtFiles) {
            String baseName = stripExtension(nbtPath.getFileName().toString());
            Path configPath = ROOT.resolve(baseName + ".json");
            Definition definition = loadDefinition(configPath, baseName);
            if (!definition.enabled) {
                continue;
            }

            ResourceLocation id = resolveId(definition.structureId, baseName);
            if (id == null) {
                ModConstants.LOGGER.warn("Skipping modpack structure {} due to invalid id in {}", nbtPath, configPath);
                continue;
            }

            NBTStructurePlacer placer = new NBTStructurePlacer(id, nbtPath);
            ENTRIES.put(id, new Entry(id, nbtPath, configPath, definition.alignToSurface, placer));
        }

        ModConstants.LOGGER.info("Loaded {} modpack structure templates from {}", ENTRIES.size(), ROOT);
    }

    public static synchronized Collection<Entry> entries() {
        return List.copyOf(ENTRIES.values());
    }

    public static synchronized Optional<Entry> get(ResourceLocation id) {
        return Optional.ofNullable(ENTRIES.get(id));
    }

    public static Path rootDirectory() {
        return ROOT;
    }

    private static Definition loadDefinition(Path configPath, String baseName) {
        if (!Files.exists(configPath)) {
            Definition def = new Definition();
            def.structureId = "wildernessodysseyapi:modpack/" + sanitizePath(baseName);
            def.displayName = baseName;
            writeDefinition(configPath, def);
            return def;
        }

        try (Reader reader = Files.newBufferedReader(configPath)) {
            Definition parsed = GSON.fromJson(reader, Definition.class);
            if (parsed == null) {
                throw new JsonSyntaxException("Empty json");
            }
            if (parsed.structureId == null || parsed.structureId.isBlank()) {
                parsed.structureId = "wildernessodysseyapi:modpack/" + sanitizePath(baseName);
            }
            return parsed;
        } catch (Exception e) {
            ModConstants.LOGGER.warn("Failed to parse modpack structure config {}. Using defaults.", configPath, e);
            Definition fallback = new Definition();
            fallback.structureId = "wildernessodysseyapi:modpack/" + sanitizePath(baseName);
            fallback.displayName = baseName;
            return fallback;
        }
    }

    private static void writeDefinition(Path configPath, Definition definition) {
        try (Writer writer = Files.newBufferedWriter(configPath)) {
            GSON.toJson(definition, writer);
        } catch (IOException e) {
            ModConstants.LOGGER.warn("Failed writing default structure config {}", configPath, e);
        }
    }

    private static void writeTemplateConfigIfMissing() throws IOException {
        Path template = ROOT.resolve("_example.json");
        if (Files.exists(template)) {
            return;
        }
        Definition sample = new Definition();
        sample.structureId = "wildernessodysseyapi:modpack/example_structure";
        sample.displayName = "Example Structure";
        sample.alignToSurface = true;
        sample.enabled = true;
        sample.notes = List.of(
                "Drop a .nbt file next to this json and rename this file to <same_name>.json",
                "structureId controls the in-game id used by /modpackstructures place <id>",
                "alignToSurface=true uses NBTStructurePlacer.placeAnchored (recommended for terrain)"
        );
        try (Writer writer = Files.newBufferedWriter(template)) {
            GSON.toJson(sample, writer);
        }
    }

    private static ResourceLocation resolveId(String configuredId, String fileFallbackName) {
        String value = configuredId;
        if (value == null || value.isBlank()) {
            value = "wildernessodysseyapi:modpack/" + sanitizePath(fileFallbackName);
        }
        return ResourceLocation.tryParse(value);
    }

    private static String sanitizePath(String raw) {
        String lower = raw.toLowerCase(Locale.ROOT);
        StringBuilder sb = new StringBuilder(lower.length());
        for (int i = 0; i < lower.length(); i++) {
            char c = lower.charAt(i);
            boolean ok = (c >= 'a' && c <= 'z') || (c >= '0' && c <= '9') || c == '_' || c == '/' || c == '-' || c == '.';
            sb.append(ok ? c : '_');
        }
        String cleaned = sb.toString();
        return cleaned.isBlank() ? "structure" : cleaned;
    }

    private static String stripExtension(String filename) {
        int idx = filename.lastIndexOf('.');
        return idx <= 0 ? filename : filename.substring(0, idx);
    }

    public record Entry(ResourceLocation id,
                        Path nbtPath,
                        Path configPath,
                        boolean alignToSurface,
                        NBTStructurePlacer placer) {
    }

    private static final class Definition {
        boolean enabled = true;
        String structureId;
        String displayName = "";
        boolean alignToSurface = true;
        List<String> notes = List.of();
    }
}
