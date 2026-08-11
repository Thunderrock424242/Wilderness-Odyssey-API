package com.thunder.wildernessodysseyapi.structuregen.content;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.resources.ResourceLocation;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;

/**
 * Read-only StructureGen catalog reconstructed from a datagen registry snapshot.
 *
 * <p>A missing optional snapshot is represented by {@link Optional#empty()}.
 * A present but malformed, stale-schema, or internally inconsistent snapshot
 * fails closed with an {@link IOException}; it is never treated as an empty
 * registry.</p>
 */
public final class JsonSnapshotStructureBlockCatalog implements StructureBlockCatalog {

    private static final Set<String> ROOT_FIELDS = Set.of(
            "schemaVersion", "environmentFingerprint", "mods", "blocks"
    );
    private static final Set<String> MOD_FIELDS = Set.of("id", "version");
    private static final Set<String> BLOCK_FIELDS = Set.of("id", "properties", "defaultProperties");
    private static final String SHA_256_PATTERN = "[0-9a-f]{64}";

    private final String environmentFingerprint;
    private final Map<String, String> installedMods;
    private final Map<ResourceLocation, AvailableBlockDescriptor> blocks;

    private JsonSnapshotStructureBlockCatalog(
            String environmentFingerprint,
            Map<String, String> installedMods,
            Map<ResourceLocation, AvailableBlockDescriptor> blocks
    ) {
        this.environmentFingerprint = requireEnvironmentFingerprint(environmentFingerprint);
        this.installedMods = immutableOrderedMap(new TreeMap<>(installedMods));
        this.blocks = immutableOrderedMap(new TreeMap<>(blocks));
    }

    /**
     * Loads a snapshot when the caller supplied an existing regular file.
     *
     * @return an empty optional only when no path was supplied or the path does not exist
     * @throws IOException when an existing snapshot cannot be read or validated
     */
    public static Optional<JsonSnapshotStructureBlockCatalog> loadOptional(Path path) throws IOException {
        if (path == null || !Files.exists(path, LinkOption.NOFOLLOW_LINKS)) {
            return Optional.empty();
        }
        if (!Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)) {
            throw new IOException("StructureGen catalog snapshot is not a regular file without following links: "
                    + path);
        }
        return Optional.of(load(path));
    }

    /** Reads and strictly validates one deterministic catalog snapshot. */
    public static JsonSnapshotStructureBlockCatalog load(Path path) throws IOException {
        if (!Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)) {
            throw new IOException("StructureGen catalog snapshot is not a regular file without following links: "
                    + path);
        }
        try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            JsonElement parsed = JsonParser.parseReader(reader);
            JsonObject root = requireObject(parsed, "root");
            rejectUnknownFields(root, ROOT_FIELDS, "root");

            int schemaVersion = requireInteger(root, "schemaVersion", "root");
            if (schemaVersion != SNAPSHOT_SCHEMA_VERSION) {
                throw new IllegalArgumentException("Unsupported StructureGen catalog schemaVersion "
                        + schemaVersion + "; expected " + SNAPSHOT_SCHEMA_VERSION + ".");
            }

            String environmentFingerprint = requireEnvironmentFingerprint(
                    requireString(root, "environmentFingerprint", "root")
            );
            Map<String, String> mods = parseMods(requireArray(root, "mods", "root"));
            Map<ResourceLocation, AvailableBlockDescriptor> blocks =
                    parseBlocks(requireArray(root, "blocks", "root"));
            return new JsonSnapshotStructureBlockCatalog(environmentFingerprint, mods, blocks);
        } catch (RuntimeException exception) {
            throw new IOException("Invalid StructureGen catalog snapshot '" + path + "': "
                    + exception.getMessage(), exception);
        }
    }

    /** Returns the exact environment hash used to produce this registry snapshot. */
    public String environmentFingerprint() {
        return environmentFingerprint;
    }

    @Override
    public Map<String, String> installedMods() {
        return installedMods;
    }

    @Override
    public Map<ResourceLocation, AvailableBlockDescriptor> blocks() {
        return blocks;
    }

    /** Serializes a catalog into the stable schema consumed by {@link #load(Path)}. */
    static JsonObject toJson(StructureBlockCatalog catalog, String environmentFingerprint) {
        JsonObject root = new JsonObject();
        root.addProperty("schemaVersion", SNAPSHOT_SCHEMA_VERSION);
        root.addProperty("environmentFingerprint", requireEnvironmentFingerprint(environmentFingerprint));

        JsonArray mods = new JsonArray();
        new TreeMap<>(catalog.installedMods()).forEach((id, version) -> {
            JsonObject mod = new JsonObject();
            mod.addProperty("id", id);
            mod.addProperty("version", version);
            mods.add(mod);
        });
        root.add("mods", mods);

        JsonArray blocks = new JsonArray();
        new TreeMap<>(catalog.blocks()).forEach((id, descriptor) -> {
            JsonObject block = new JsonObject();
            block.addProperty("id", id.toString());

            JsonObject properties = new JsonObject();
            descriptor.properties().forEach((name, values) -> {
                JsonArray allowedValues = new JsonArray();
                values.forEach(allowedValues::add);
                properties.add(name, allowedValues);
            });
            block.add("properties", properties);

            JsonObject defaults = new JsonObject();
            descriptor.defaultProperties().forEach(defaults::addProperty);
            block.add("defaultProperties", defaults);
            blocks.add(block);
        });
        root.add("blocks", blocks);
        return root;
    }

    /**
     * Validates the stable lowercase hexadecimal representation of a SHA-256 environment hash.
     *
     * <p>Both the datagen producer and offline consumer use this boundary so a fingerprint cannot
     * be normalized differently on either side or silently accept a truncated/uppercase value.</p>
     */
    public static String requireEnvironmentFingerprint(String fingerprint) {
        if (fingerprint == null || !fingerprint.matches(SHA_256_PATTERN)) {
            throw new IllegalArgumentException(
                    "StructureGen catalog environment fingerprint must be exactly 64 lowercase hexadecimal characters."
            );
        }
        return fingerprint;
    }

    private static Map<String, String> parseMods(JsonArray array) {
        Map<String, String> mods = new TreeMap<>();
        for (int index = 0; index < array.size(); index++) {
            String location = "mods[" + index + "]";
            JsonObject mod = requireObject(array.get(index), location);
            rejectUnknownFields(mod, MOD_FIELDS, location);
            String id = requireString(mod, "id", location);
            String version = requireString(mod, "version", location);
            if (id.isBlank() || version.isBlank()) {
                throw new IllegalArgumentException(location + " must contain non-blank id and version strings.");
            }
            if (mods.putIfAbsent(id, version) != null) {
                throw new IllegalArgumentException("Duplicate mod ID '" + id + "' in catalog snapshot.");
            }
        }
        return mods;
    }

    private static Map<ResourceLocation, AvailableBlockDescriptor> parseBlocks(JsonArray array) {
        Map<ResourceLocation, AvailableBlockDescriptor> blocks = new TreeMap<>();
        for (int index = 0; index < array.size(); index++) {
            String location = "blocks[" + index + "]";
            JsonObject block = requireObject(array.get(index), location);
            rejectUnknownFields(block, BLOCK_FIELDS, location);

            String rawId = requireString(block, "id", location);
            ResourceLocation id = ResourceLocation.tryParse(rawId);
            if (id == null || !rawId.contains(":") || !id.toString().equals(rawId)) {
                throw new IllegalArgumentException(location + ".id is not an explicit resource location: " + rawId);
            }
            Map<String, List<String>> properties = parsePropertyDomains(
                    requireObject(block.get("properties"), location + ".properties"), location + ".properties"
            );
            Map<String, String> defaults = parseDefaults(
                    requireObject(block.get("defaultProperties"), location + ".defaultProperties"),
                    location + ".defaultProperties"
            );
            AvailableBlockDescriptor descriptor = new AvailableBlockDescriptor(id, properties, defaults);
            if (blocks.putIfAbsent(id, descriptor) != null) {
                throw new IllegalArgumentException("Duplicate block ID '" + id + "' in catalog snapshot.");
            }
        }
        return blocks;
    }

    private static Map<String, List<String>> parsePropertyDomains(JsonObject object, String location) {
        Map<String, List<String>> properties = new TreeMap<>();
        object.entrySet().forEach(entry -> {
            JsonArray values = requireArray(entry.getValue(), location + "." + entry.getKey());
            List<String> parsedValues = new java.util.ArrayList<>();
            for (int index = 0; index < values.size(); index++) {
                parsedValues.add(requireString(values.get(index), location + "." + entry.getKey() + "[" + index + "]"));
            }
            properties.put(entry.getKey(), parsedValues);
        });
        return properties;
    }

    private static Map<String, String> parseDefaults(JsonObject object, String location) {
        Map<String, String> defaults = new TreeMap<>();
        object.entrySet().forEach(entry -> defaults.put(
                entry.getKey(), requireString(entry.getValue(), location + "." + entry.getKey())
        ));
        return defaults;
    }

    private static JsonObject requireObject(JsonElement value, String location) {
        if (value == null || !value.isJsonObject()) {
            throw new IllegalArgumentException(location + " must be a JSON object.");
        }
        return value.getAsJsonObject();
    }

    private static JsonArray requireArray(JsonObject object, String field, String location) {
        return requireArray(object.get(field), location + "." + field);
    }

    private static JsonArray requireArray(JsonElement value, String location) {
        if (value == null || !value.isJsonArray()) {
            throw new IllegalArgumentException(location + " must be a JSON array.");
        }
        return value.getAsJsonArray();
    }

    private static int requireInteger(JsonObject object, String field, String location) {
        JsonElement value = object.get(field);
        if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isNumber()
                || !value.getAsString().matches("-?[0-9]+")) {
            throw new IllegalArgumentException(location + "." + field + " must be an integer.");
        }
        return value.getAsInt();
    }

    private static String requireString(JsonObject object, String field, String location) {
        return requireString(object.get(field), location + "." + field);
    }

    private static String requireString(JsonElement value, String location) {
        if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString()) {
            throw new IllegalArgumentException(location + " must be a string.");
        }
        return value.getAsString();
    }

    private static void rejectUnknownFields(JsonObject object, Set<String> allowed, String location) {
        object.keySet().stream()
                .filter(field -> !allowed.contains(field))
                .sorted()
                .findFirst()
                .ifPresent(field -> {
                    throw new IllegalArgumentException(location + " contains unknown field '" + field + "'.");
                });
    }

    private static <K, V> Map<K, V> immutableOrderedMap(Map<K, V> source) {
        return Collections.unmodifiableMap(new LinkedHashMap<>(source));
    }
}
