package com.thunder.wildernessodysseyapi.weather.simulation;

import java.util.function.ToDoubleFunction;

/** First-order upwind finite-volume transport; every shared face uses the same old-state flux. */
public final class AtmosphericTransport {
    private AtmosphericTransport() { }

    /** Conservative signed inventory delta; caller must honor the four-face CFL bound. */
    public static double delta(AtmosphericPhysicalState center,
            AtmosphereSimulationEngine.PhysicalNeighborhood neighbors, ToDoubleFunction<AtmosphericPhysicalState> value,
            int layerIndex, double seconds, double cellSizeMeters, double transportScale) {
        double q = value.applyAsDouble(center);
        double east = (neighbors.closedFaces() & 2) != 0 ? 0 : face(q, value.applyAsDouble(neighbors.east()), center.column().layer(layerIndex),
                neighbors.east().column().layer(layerIndex), true);
        double west = (neighbors.closedFaces() & 8) != 0 ? 0 : face(value.applyAsDouble(neighbors.west()), q, neighbors.west().column().layer(layerIndex),
                center.column().layer(layerIndex), true);
        double south = (neighbors.closedFaces() & 4) != 0 ? 0 : face(q, value.applyAsDouble(neighbors.south()), center.column().layer(layerIndex),
                neighbors.south().column().layer(layerIndex), false);
        double north = (neighbors.closedFaces() & 1) != 0 ? 0 : face(value.applyAsDouble(neighbors.north()), q, neighbors.north().column().layer(layerIndex),
                center.column().layer(layerIndex), false);
        return (west - east + north - south) * seconds / cellSizeMeters * AtmosphericUnits.unit(transportScale);
    }

    /** Intensive properties advect without compression heating a uniform temperature field. */
    public static double intensiveDelta(AtmosphericPhysicalState center,
            AtmosphereSimulationEngine.PhysicalNeighborhood neighbors, ToDoubleFunction<AtmosphericPhysicalState> value,
            int layerIndex, double seconds, double cellSizeMeters, double transportScale) {
        return delta(center, neighbors, value, layerIndex, seconds, cellSizeMeters, transportScale)
                - value.applyAsDouble(center) * delta(center, neighbors, ignored -> 1.0,
                layerIndex, seconds, cellSizeMeters, transportScale);
    }

    private static double face(double left, double right, AtmosphericLayer a, AtmosphericLayer b, boolean x) {
        double speed = x ? (a.windXMetresPerSecond() + b.windXMetresPerSecond()) * 0.5
                : (a.windZMetresPerSecond() + b.windZMetresPerSecond()) * 0.5;
        return speed * (speed >= 0 ? left : right);
    }
}
