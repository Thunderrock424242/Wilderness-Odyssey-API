package com.thunder.wildernessodysseyapi.ecosystem.simulation;

import com.thunder.wildernessodysseyapi.ecosystem.api.WildlifeSimulationLod;

import java.util.List;

/** Pure distance policy shared by the runtime manager and unit tests. */
public final class EcosystemZoneClassifier {

    private EcosystemZoneClassifier() {
    }

    /** Classifies one exact horizontal position against the nearest relevant player. */
    public static WildlifeSimulationLod classifyPosition(
            double x,
            double z,
            List<PlayerPoint> players,
            EcosystemSimulationSettings settings
    ) {
        return classifySquared(nearestDistanceSquared(x, z, players), settings);
    }

    /**
     * Classifies a complete cell using the closest point on its footprint.
     *
     * <p>This deliberately favors the more active level at cell edges so an
     * approaching player cannot stand beside a frozen animal in the same cell.</p>
     */
    public static WildlifeSimulationLod classifyCell(
            EcosystemCellKey key,
            List<PlayerPoint> players,
            EcosystemSimulationSettings settings
    ) {
        if (players.isEmpty()) {
            return WildlifeSimulationLod.DORMANT;
        }
        double minimumX = key.minimumBlockX(settings.cellSize());
        double minimumZ = key.minimumBlockZ(settings.cellSize());
        double maximumX = minimumX + settings.cellSize();
        double maximumZ = minimumZ + settings.cellSize();
        double nearest = Double.POSITIVE_INFINITY;
        for (PlayerPoint player : players) {
            double dx = axisDistance(player.x(), minimumX, maximumX);
            double dz = axisDistance(player.z(), minimumZ, maximumZ);
            nearest = Math.min(nearest, dx * dx + dz * dz);
        }
        return classifySquared(nearest, settings);
    }

    /** Returns exact horizontal distance to the nearest relevant player. */
    public static double nearestDistance(double x, double z, List<PlayerPoint> players) {
        return Math.sqrt(nearestDistanceSquared(x, z, players));
    }

    private static double nearestDistanceSquared(double x, double z, List<PlayerPoint> players) {
        double nearest = Double.POSITIVE_INFINITY;
        for (PlayerPoint player : players) {
            double dx = player.x() - x;
            double dz = player.z() - z;
            nearest = Math.min(nearest, dx * dx + dz * dz);
        }
        return nearest;
    }

    private static WildlifeSimulationLod classifySquared(
            double distanceSquared,
            EcosystemSimulationSettings settings
    ) {
        if (distanceSquared <= square(settings.activeRadius())) {
            return WildlifeSimulationLod.ACTIVE;
        }
        if (distanceSquared <= square(settings.nearRadius())) {
            return WildlifeSimulationLod.NEAR;
        }
        if (distanceSquared <= square(settings.distantRadius())) {
            return WildlifeSimulationLod.DISTANT;
        }
        return WildlifeSimulationLod.DORMANT;
    }

    private static double axisDistance(double value, double minimum, double maximum) {
        if (value < minimum) {
            return minimum - value;
        }
        if (value > maximum) {
            return value - maximum;
        }
        return 0.0;
    }

    private static double square(double value) {
        return value * value;
    }

    /** Minimal player projection that keeps the classifier independent of live server objects. */
    public record PlayerPoint(double x, double z) {
    }
}
