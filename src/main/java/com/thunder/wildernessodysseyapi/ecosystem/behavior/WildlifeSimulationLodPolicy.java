package com.thunder.wildernessodysseyapi.ecosystem.behavior;

import com.thunder.wildernessodysseyapi.ecosystem.api.WildlifeSimulationLod;

import java.util.UUID;

/** Pure distance and scheduling policy for environmental-AI level of detail. */
public final class WildlifeSimulationLodPolicy {

    private WildlifeSimulationLodPolicy() {
    }

    /** Classifies one nearest-player distance against ascending LOD boundaries. */
    public static WildlifeSimulationLod classify(
            double nearestPlayerDistanceSquared,
            int activeDistance,
            int nearDistance,
            int distantDistance
    ) {
        if (!Double.isFinite(nearestPlayerDistanceSquared) || nearestPlayerDistanceSquared < 0.0) {
            return WildlifeSimulationLod.DORMANT;
        }
        if (nearestPlayerDistanceSquared <= square(activeDistance)) {
            return WildlifeSimulationLod.ACTIVE;
        }
        if (nearestPlayerDistanceSquared <= square(Math.max(activeDistance, nearDistance))) {
            return WildlifeSimulationLod.NEAR;
        }
        if (nearestPlayerDistanceSquared <= square(Math.max(nearDistance, distantDistance))) {
            return WildlifeSimulationLod.DISTANT;
        }
        return WildlifeSimulationLod.DORMANT;
    }

    /**
     * Returns a stable, spread-out interval for one animal and LOD.
     * Multipliers make lower-detail tiers progressively cheaper.
     */
    public static long staggeredInterval(
            long baseTicks,
            WildlifeSimulationLod lod,
            int nearMultiplier,
            int distantMultiplier,
            int dormantMultiplier,
            UUID animalId
    ) {
        long multiplier = switch (lod) {
            case ACTIVE -> 1L;
            case NEAR -> Math.max(1, nearMultiplier);
            case DISTANT -> Math.max(1, distantMultiplier);
            case DORMANT -> Math.max(1, dormantMultiplier);
        };
        long scaled = Math.max(10L, Math.min(72_000L, Math.max(1L, baseTicks) * multiplier));
        long mixed = animalId.getMostSignificantBits()
                ^ Long.rotateLeft(animalId.getLeastSignificantBits(), 17);
        long jitterRange = Math.max(1L, scaled / 3L);
        return scaled + Math.floorMod(mixed, jitterRange);
    }

    private static double square(int value) {
        double safe = Math.max(0, value);
        return safe * safe;
    }
}
