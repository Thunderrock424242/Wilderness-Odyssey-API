package com.thunder.wildernessodysseyapi.worldgen.biome;

import com.thunder.wildernessodysseyapi.core.ModEntities;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.level.biome.MobSpawnSettings;

/** Supplies the rift-only spawn profile when the biome is bootstrapped in code. */
public final class AnomalyBiomeMobSettings {
    private AnomalyBiomeMobSettings() {
    }

    /** Mirrors the data-pack biome's Riftborn, Listener, and Wraith population. */
    public static void addForestSpawns(MobSpawnSettings.Builder builder) {
        builder.addSpawn(MobCategory.MONSTER, new MobSpawnSettings.SpawnerData(ModEntities.RIFTBORN.get(), 80, 2, 4));
        builder.addSpawn(MobCategory.MONSTER, new MobSpawnSettings.SpawnerData(ModEntities.RIFT_LISTENER.get(), 10, 1, 1));
        builder.addSpawn(MobCategory.MONSTER, new MobSpawnSettings.SpawnerData(ModEntities.RIFTBOUND_WRAITH.get(), 4, 1, 1));
    }
}
