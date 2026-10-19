package com.thunder.wildernessodysseyapi.ecosystem.api;

import com.thunder.wildernessodysseyapi.watersystem.water.worldgen.river.RiverChannelColumn;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;

/**
 * Immutable observations for wildlife integrations, with no spawning or water
 * mutation authority. Depth is the current loaded water column's depth; air
 * temperature comes from the existing climate service and is not a measured
 * water temperature. Waterbody identity follows versioned generated reach data.
 */
public record RiverHabitat(long waterbodyId, ResourceLocation biome, RiverChannelColumn.Kind kind,
                           double surfaceY, double depth, Vec3 currentMetresPerSecond,
                           float clarity, float sediment, double airTemperatureCelsius, boolean flooding) {
}
