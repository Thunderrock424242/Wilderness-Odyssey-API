package com.thunder.wildernessodysseyapi.WorldGen.modpack;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
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
 * and exposes runtime placers plus worldgen scaffold generation for pack authors.
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
            ENTRIES.put(id, new Entry(id, nbtPath, configPath, definition.alignToSurface, definition, placer));
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

    public static synchronized int generateAllWorldgenScaffolds() {
        int written = 0;
        for (Entry entry : ENTRIES.values()) {
            try {
                generateWorldgenScaffold(entry);
                written++;
            } catch (IOException e) {
                ModConstants.LOGGER.warn("Failed generating worldgen scaffold for {}", entry.id(), e);
            }
        }
        return written;
    }

    public static synchronized boolean generateWorldgenScaffold(ResourceLocation id) {
        Entry entry = ENTRIES.get(id);
        if (entry == null) {
            return false;
        }
        try {
            generateWorldgenScaffold(entry);
            return true;
        } catch (IOException e) {
            ModConstants.LOGGER.warn("Failed generating worldgen scaffold for {}", id, e);
            return false;
        }
    }

    private static void generateWorldgenScaffold(Entry entry) throws IOException {
        Definition def = entry.definition();
        ResourceLocation id = entry.id();

        Path datapackRoot = ROOT.resolve("generated_datapack");
        Path dataRoot = datapackRoot.resolve("data").resolve(id.getNamespace());

        Path structures = dataRoot.resolve("structures").resolve(id.getPath() + ".nbt");
        Files.createDirectories(structures.getParent());
        Files.copy(entry.nbtPath(), structures, java.nio.file.StandardCopyOption.REPLACE_EXISTING);

        Path structureJson = dataRoot.resolve("worldgen/structure").resolve(id.getPath() + ".json");
        writeJson(structureJson, buildStructureJson(id, def));

        Path structureSetJson = dataRoot.resolve("worldgen/structure_set").resolve(id.getPath() + ".json");
        writeJson(structureSetJson, buildStructureSetJson(id, def));

        ResourceLocation biomeTag = ResourceLocation.tryParse(def.biomeTag);
        if (biomeTag == null) {
            biomeTag = ResourceLocation.fromNamespaceAndPath("minecraft", "is_overworld");
        }
        Path biomeTagJson = datapackRoot.resolve("data")
                .resolve(biomeTag.getNamespace())
                .resolve("tags/worldgen/biome")
                .resolve(biomeTag.getPath() + ".json");

        JsonObject biomeTagObj = new JsonObject();
        biomeTagObj.addProperty("replace", false);
        JsonArray values = new JsonArray();
        values.add("minecraft:plains");
        biomeTagObj.add("values", values);
        writeJsonIfMissing(biomeTagJson, biomeTagObj);

        Path hasStructureTagJson = dataRoot.resolve("tags/worldgen/biome/has_structure").resolve(id.getPath() + ".json");
        JsonObject hasStructureObj = new JsonObject();
        hasStructureObj.addProperty("replace", false);
        JsonArray hasValues = new JsonArray();
        hasValues.add("#" + biomeTag.getNamespace() + ":" + biomeTag.getPath());
        hasStructureObj.add("values", hasValues);
        writeJson(hasStructureTagJson, hasStructureObj);

        writePackMcmetaIfMissing(datapackRoot);
    }

    private static JsonObject buildStructureJson(ResourceLocation id, Definition def) {
        JsonObject root = new JsonObject();
        root.addProperty("type", "minecraft:jigsaw");
        root.addProperty("biomes", "#" + def.biomeTag);
        root.addProperty("step", def.generationStep);
        root.addProperty("terrain_adaptation", def.terrainAdaptation);
        root.addProperty("start_height", 0);

        JsonObject startPool = new JsonObject();
        startPool.addProperty("element_type", "minecraft:single_pool_element");
        startPool.addProperty("location", id.toString());
        startPool.addProperty("projection", "rigid");

        JsonObject pool = new JsonObject();
        pool.addProperty("name", id + "_pool");
        JsonArray elements = new JsonArray();
        JsonObject weighted = new JsonObject();
        weighted.add("element", startPool);
        weighted.addProperty("weight", 1);
        elements.add(weighted);
        pool.add("elements", elements);
        pool.addProperty("fallback", "minecraft:empty");

        root.add("start_pool_inline", pool);
        root.addProperty("size", 1);
        root.addProperty("max_distance_from_center", 80);
        root.addProperty("use_expansion_hack", false);
        return root;
    }

    private static JsonObject buildStructureSetJson(ResourceLocation id, Definition def) {
        JsonObject root = new JsonObject();
        JsonArray structures = new JsonArray();
        JsonObject structure = new JsonObject();
        structure.addProperty("structure", id.toString());
        structure.addProperty("weight", 1);
        structures.add(structure);
        root.add("structures", structures);

        JsonObject placement = new JsonObject();
        placement.addProperty("type", "minecraft:random_spread");
        placement.addProperty("spacing", def.spacing);
        placement.addProperty("separation", def.separation);
        placement.addProperty("salt", def.salt);
        root.add("placement", placement);
        return root;
    }

    private static void writePackMcmetaIfMissing(Path datapackRoot) throws IOException {
        Path packMeta = datapackRoot.resolve("pack.mcmeta");
        if (Files.exists(packMeta)) {
            return;
        }
        JsonObject root = new JsonObject();
        JsonObject pack = new JsonObject();
        pack.addProperty("pack_format", 61);
        pack.addProperty("description", "Generated modpack structures for Wilderness Odyssey API");
        root.add("pack", pack);
        writeJson(packMeta, root);
    }

    private static Definition loadDefinition(Path configPath, String baseName) {
        if (!Files.exists(configPath)) {
            Definition def = createDefaultDefinition(baseName);
            writeDefinition(configPath, def);
            return def;
        }

        try (Reader reader = Files.newBufferedReader(configPath)) {
            Definition parsed = GSON.fromJson(reader, Definition.class);
            if (parsed == null) {
                throw new JsonSyntaxException("Empty json");
            }
            Definition defaults = createDefaultDefinition(baseName);
            parsed.normalize(defaults);
            return parsed;
        } catch (Exception e) {
            ModConstants.LOGGER.warn("Failed to parse modpack structure config {}. Using defaults.", configPath, e);
            return createDefaultDefinition(baseName);
        }
    }

    private static Definition createDefaultDefinition(String baseName) {
        Definition def = new Definition();
        def.structureId = "wildernessodysseyapi:modpack/" + sanitizePath(baseName);
        def.displayName = baseName;
        return def;
    }

    private static void writeDefinition(Path configPath, Definition definition) {
        try (Writer writer = Files.newBufferedWriter(configPath)) {
            GSON.toJson(definition, writer);
        } catch (IOException e) {
            ModConstants.LOGGER.warn("Failed writing default structure config {}", configPath, e);
        }
    }

    private static void writeJson(Path path, JsonObject obj) throws IOException {
        Files.createDirectories(path.getParent());
        try (Writer writer = Files.newBufferedWriter(path)) {
            GSON.toJson(obj, writer);
        }
    }

    private static void writeJsonIfMissing(Path path, JsonObject obj) throws IOException {
        if (Files.exists(path)) {
            return;
        }
        writeJson(path, obj);
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
        sample.biomeTag = "minecraft:is_overworld";
        sample.generationStep = "surface_structures";
        sample.terrainAdaptation = "beard_thin";
        sample.spacing = 36;
        sample.separation = 12;
        sample.salt = 150001;
        sample.notes = List.of(
                "Drop a .nbt file next to this json and rename this file to <same_name>.json",
                "structureId controls the in-game id used by /modpackstructures place <id>",
                "Use /modpackstructures scaffold to generate datapack structure + structure_set templates"
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
                        Definition definition,
                        NBTStructurePlacer placer) {
    }

    public static final class Definition {
        boolean enabled = true;
        String structureId;
        String displayName = "";
        boolean alignToSurface = true;
        String biomeTag = "minecraft:is_overworld";
        String generationStep = "surface_structures";
        String terrainAdaptation = "beard_thin";
        int spacing = 36;
        int separation = 12;
        int salt = 150001;
        List<String> notes = List.of();

        void normalize(Definition defaults) {
            if (structureId == null || structureId.isBlank()) structureId = defaults.structureId;
            if (displayName == null) displayName = defaults.displayName;
            if (biomeTag == null || biomeTag.isBlank()) biomeTag = defaults.biomeTag;
            if (generationStep == null || generationStep.isBlank()) generationStep = defaults.generationStep;
            if (terrainAdaptation == null || terrainAdaptation.isBlank()) terrainAdaptation = defaults.terrainAdaptation;
            spacing = Math.max(2, spacing);
            separation = Math.max(1, Math.min(separation, spacing - 1));
        }
    }
}
