package com.thunder.wildernessodysseyapi.watersystem.water.compat.vanilla;

import com.thunder.wildernessodysseyapi.core.ModConstants;
import com.thunder.wildernessodysseyapi.watersystem.water.config.WaterSimulationConfig;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.AbstractFish;

/** Determines whether a native water-entry transition merits an impact sound. */
public final class WaterEntrySplashPolicy {

    public static final TagKey<EntityType<?>> QUIET_WATER_ENTRY = TagKey.create(
            Registries.ENTITY_TYPE,
            ResourceLocation.fromNamespaceAndPath(ModConstants.MOD_ID, "quiet_water_entry"));
    private static final double MINIMUM_FALL_BLOCKS = 3.0;

    private WaterEntrySplashPolicy() {
    }

    /** Reads only pre-reset fall distance and fish classification, never world or chunk state. */
    public static boolean shouldPlay(Entity entity) {
        boolean fish = entity instanceof AbstractFish || entity.getType().is(QUIET_WATER_ENTRY);
        return shouldPlay(entity.fallDistance, fish, WaterSimulationConfig.minimumSplashFallDistanceBlocks());
    }

    /** Inclusive fall threshold shared by native generic, hostile, and player entry sounds. */
    public static boolean shouldPlay(float fallDistance, boolean fish, double minimumFallDistance) {
        double threshold = Double.isFinite(minimumFallDistance)
                ? Math.max(MINIMUM_FALL_BLOCKS, minimumFallDistance) : MINIMUM_FALL_BLOCKS;
        return !fish && Float.isFinite(fallDistance) && fallDistance >= threshold;
    }
}
