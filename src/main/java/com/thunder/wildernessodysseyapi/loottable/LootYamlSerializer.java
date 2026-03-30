package com.thunder.wildernessodysseyapi.loottable;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import net.minecraft.world.level.storage.loot.LootTable;
import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.error.YAMLException;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

/**
 * Utility for round-tripping Minecraft loot tables between codec-backed JSON
 * and human-editable YAML files.
 */
public class LootYamlSerializer {

    private static final DumperOptions OPTIONS = new DumperOptions();
    private static final Yaml YAML;

    static {
        OPTIONS.setPrettyFlow(true);
        OPTIONS.setDefaultFlowStyle(DumperOptions.FlowStyle.BLOCK);
        OPTIONS.setIndent(2);
        OPTIONS.setDefaultScalarStyle(DumperOptions.ScalarStyle.PLAIN);
        YAML = new Yaml(OPTIONS);
    }

    /**
     * Writes a loot table to YAML with pretty formatting.
     *
     * @param path Destination path for YAML output.
     * @param table Loot table to serialize.
     */
    public static void writeLootTable(Path path, LootTable table) throws IOException {
        JsonElement json = LootTable.DIRECT_CODEC.encodeStart(JsonOps.INSTANCE, table).getOrThrow(error -> new IllegalStateException(error));
        var gson = new GsonBuilder().setPrettyPrinting().create();
        Map<String, Object> map = YAML.load(gson.toJson(json));
        Files.writeString(path, YAML.dump(map));
    }

    /**
     * Reads a YAML loot table and falls back to a JSON snapshot when YAML parsing
     * or codec validation fails.
     *
     * @param yamlPath Primary YAML path.
     * @param jsonFallbackPath Backup JSON path.
     * @return Parsed loot table.
     */
    public static LootTable readLootTableWithFallback(Path yamlPath, Path jsonFallbackPath) throws IOException {
        var gson = new GsonBuilder().create();

        try {
            String yaml = Files.readString(yamlPath);
            Object parsed = YAML.load(yaml);
            String json = gson.toJson(parsed);
            JsonElement element = JsonParser.parseString(json);
            return LootTable.DIRECT_CODEC.parse(JsonOps.INSTANCE, element).getOrThrow(error -> new IllegalStateException(error));
        } catch (YAMLException | IllegalStateException e) {
            System.err.println("[LootYamlSerializer] Invalid YAML: " + yamlPath + ": " + e.getMessage());
            if (Files.exists(jsonFallbackPath)) {
                System.err.println("[LootYamlSerializer] Fallback to JSON: " + jsonFallbackPath);
                JsonElement element = JsonParser.parseString(Files.readString(jsonFallbackPath));
                return LootTable.DIRECT_CODEC.parse(JsonOps.INSTANCE, element).getOrThrow(error -> new IllegalStateException(error));
            } else {
                throw new IOException("YAML failed and JSON fallback not found for: " + yamlPath);
            }
        }
    }

    private static Object treeToJson(LootTable table) {
        JsonElement json = LootTable.DIRECT_CODEC.encodeStart(JsonOps.INSTANCE, table).getOrThrow(error -> new IllegalStateException(error));
        return JsonParser.parseString(json.toString());
    }
}
