package com.thunder.wildernessodysseyapi.loottable;

import com.google.gson.GsonBuilder;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.util.GsonHelper;
import net.minecraft.world.level.storage.loot.LootTable;
import com.mojang.serialization.JsonOps;
import net.neoforged.neoforge.event.AddReloadListenerEvent;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

public class LootTableExporter {

    private static final Path CONFIG_DIR = Path.of("config", "loot_tables");

    public static void onReload(AddReloadListenerEvent event) {
        event.addListener((barrier, manager, profiler, prepareProfiler, applyProfiler, executor) -> {
            return CompletableFuture.runAsync(() -> exportLootTables(manager));
        });
    }

    private static void exportLootTables(ResourceManager manager) {
        Map<ResourceLocation, Resource> resources = manager.listResources("loot_tables", rl -> rl.getPath().endsWith(".json"));
        java.util.Map<ResourceLocation, LootTable> tables = new java.util.HashMap<>();
        for (var entry : resources.entrySet()) {
            try (var reader = entry.getValue().openAsReader()) {
                var json = GsonHelper.parse(reader);
                LootTable table = LootTable.DIRECT_CODEC.parse(JsonOps.INSTANCE, json)
                        .getOrThrow(error -> new IllegalStateException(error));
                tables.put(entry.getKey(), table);
            } catch (IOException ex) {
                System.err.println("[LootTableExporter] Failed to read loot table " + entry.getKey() + ": " + ex.getMessage());
            }
        }

        for (var entry : tables.entrySet()) {
            ResourceLocation id = entry.getKey();
            if (!id.getPath().startsWith("chests/")) continue;

            String modId = id.getNamespace();
            String name = id.getPath().substring("chests/".length());
            Path yaml = CONFIG_DIR.resolve(modId).resolve(name + ".yaml");
            Path json = CONFIG_DIR.resolve(modId).resolve(name + ".json");

            try {
                Files.createDirectories(yaml.getParent());

                if (!Files.exists(yaml)) {
                    LootYamlSerializer.writeLootTable(yaml, entry.getValue());
                }

                if (!Files.exists(json)) {
                    var gson = new GsonBuilder().setPrettyPrinting().create();
                    Files.writeString(json, gson.toJson(entry.getValue()));
                }

            } catch (IOException e) {
                System.err.println("[LootTableExporter] Failed to export " + id + ": " + e.getMessage());
            }
        }
    }
}