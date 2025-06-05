package com.thunder.wildernessodysseyapi.WorldGenClasses_and_packages.events;

import com.thunder.wildernessodysseyapi.WorldGenClasses_and_packages.worldgen.structures.MeteorStructureSpawner;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.level.LevelEvent;

public class WorldEvents {
    @SubscribeEvent
    public void onWorldLoad(LevelEvent.Load event) {
        if (event.getLevel() instanceof ServerLevel level) {
            // As soon as the server‐level is loaded, place the bunker
            MeteorStructureSpawner.tryPlace(level);
        }
    }
}
