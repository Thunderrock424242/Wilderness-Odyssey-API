package com.thunder.wildernessodysseyapi.loottable;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.storage.loot.LootTable;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.event.LootTableLoadEvent;
import net.neoforged.neoforge.common.NeoForge;

import java.io.IOException;
import java.nio.file.Path;

public class LootTableOverrideHandler {

    private static final Path CONFIG_DIR = Path.of("config", "loot_tables");

    public static void init(FMLCommonSetupEvent event) {
        NeoForge.EVENT_BUS.addListener(EventPriority.HIGH, LootTableOverrideHandler::onLootLoad);
    }

    public static void onLootLoad(LootTableLoadEvent event) {
        ResourceLocation id = event.getName();
        if (!id.getPath().startsWith("chests/")) return;

        String modId = id.getNamespace();
        String name = id.getPath().substring("chests/".length());

        Path yamlPath = CONFIG_DIR.resolve(modId).resolve(name + ".yaml");
        Path jsonPath = CONFIG_DIR.resolve(modId).resolve(name + ".json");

        try {
            LootTable table = LootYamlSerializer.readLootTableWithFallback(yamlPath, jsonPath);
            event.setTable(table);
        } catch (IOException e) {
            System.err.println("[LootTableOverrideHandler] Failed to load override for " + id + ": " + e.getMessage());
        }
    }
}