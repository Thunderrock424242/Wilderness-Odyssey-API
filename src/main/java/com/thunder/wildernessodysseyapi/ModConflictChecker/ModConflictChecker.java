package com.thunder.wildernessodysseyapi.ModConflictChecker;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerStartingEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.HashMap;
import java.util.Map;

import static com.thunder.wildernessodysseyapi.MainModClass.WildernessOdysseyAPIMainModClass.MOD_ID;

@EventBusSubscriber(modid = MOD_ID)
public class ModConflictChecker {
    private static final Logger LOGGER = LogManager.getLogger();

    // DeferredRegister for structures
    private static final DeferredRegister<Structure> STRUCTURES = DeferredRegister.create(Structure.REGISTRY, "structurechecker");

    // Map to track registered structures and their sources
    private static final Map<ResourceLocation, String> structureSources = new HashMap<>();

    // Method to register structures with error handling for ResourceLocation
    public static DeferredHolder<Structure, Structure> registerStructure(String name, Structure structure) {
        ResourceLocation structureKey = ResourceLocation.tryParse(name);
        if (structureKey == null) {
            LOGGER.error("Invalid ResourceLocation for structure: '{}'. Registration skipped.", name);
            throw new IllegalArgumentException("Invalid ResourceLocation: " + name);
        }
        return STRUCTURES.register(structureKey.getPath(), () -> structure);
    }

    // Check for conflicts during server start
    @EventBusSubscriber
    public static class EventHandlers {
        @SubscribeEvent
        public static void onServerStart(ServerStartingEvent event) {
            STRUCTURES.getEntries().forEach(entry -> {
                ResourceLocation structureKey = entry.getId();
                String modNamespace = structureKey.getNamespace();

                // Check for conflicts
                if (structureSources.containsKey(structureKey)) {
                    String otherMod = structureSources.get(structureKey);
                    if (!otherMod.equals(modNamespace)) {
                        LOGGER.error("Conflict detected: Structure '{}' is being registered by both '{}' and '{}'",
                                structureKey, otherMod, modNamespace);
                    }
                } else {
                    structureSources.put(structureKey, modNamespace);
                }
            });
        }
    }
}