package com.thunder.wildernessodysseyapi.WorldGen.events;

import com.thunder.wildernessodysseyapi.WorldGen.worldgen.structures.MeteorStructureSpawner;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.level.LevelEvent;

/****
 * WorldEvents for the Wilderness Odyssey API mod.
 */
public class WorldEvents {
    @SubscribeEvent
    public void onWorldLoad(LevelEvent.Load event) {
        if (event.getLevel() instanceof ServerLevel level) {
            // Schedule the meteor impact zone and bunker shortly after the first player joins
            MeteorStructureSpawner.scheduleInitialPlacement(level);
        }
    }
}
