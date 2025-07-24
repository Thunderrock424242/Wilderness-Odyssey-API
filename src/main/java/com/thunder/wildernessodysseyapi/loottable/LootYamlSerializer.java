package com.thunder.wildernessodysseyapi.loottable;

import net.minecraft.world.level.storage.loot.LootTable;
import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.error.YAMLException;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

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

    public static void writeLootTable(Path path, LootTable table) throws IOException {
        var gson = LootDataType.LOOT_TABLE.createCodec().toGson();
        Map<String, Object> map = YAML.load(gson.toJson(treeToJson(table)));
        Files.writeString(path, YAML.dump(map));
    }

    public static LootTable readLootTableWithFallback(Path yamlPath, Path jsonFallbackPath) throws IOException {
        var gson = LootDataType.LOOT_TABLE.createCodec().toGson();

        try {
            String yaml = Files.readString(yamlPath);
            Object parsed = YAML.load(yaml);
            String json = gson.toJson(parsed);
            return gson.fromJson(json, LootTable.class);
        } catch (YAMLException | IllegalStateException e) {
            System.err.println("[LootYamlSerializer] Invalid YAML: " + yamlPath + ": " + e.getMessage());
            if (Files.exists(jsonFallbackPath)) {
                System.err.println("[LootYamlSerializer] Fallback to JSON: " + jsonFallbackPath);
                return gson.fromJson(Files.readString(jsonFallbackPath), LootTable.class);
            } else {
                throw new IOException("YAML failed and JSON fallback not found for: " + yamlPath);
            }
        }
    }

    private static Object treeToJson(LootTable table) {
        var gson = LootDataType.LOOT_TABLE.createCodec().toGson();
        return gson.fromJson(gson.toJson(table), Object.class);
    }
}