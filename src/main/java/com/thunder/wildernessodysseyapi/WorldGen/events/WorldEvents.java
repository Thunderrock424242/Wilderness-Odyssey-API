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
            // Place the meteor impact zone and bunker when the world loads
            MeteorStructureSpawner.tryPlace(level);
        }
    }
}
