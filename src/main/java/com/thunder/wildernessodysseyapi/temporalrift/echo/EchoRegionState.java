package com.thunder.wildernessodysseyapi.temporalrift.echo;

import net.minecraft.core.BlockPos;
import org.jetbrains.annotations.Nullable;

/** Immutable query result; fracture coordinates are for server diagnostics, never broadcast globally. */
public record EchoRegionState(long regionId, EchoStabilityLevel stability, double intensity,
                              @Nullable BlockPos nearestFracture, double fractureDistance,
                              double fractureInfluence, boolean overridden) {
}
