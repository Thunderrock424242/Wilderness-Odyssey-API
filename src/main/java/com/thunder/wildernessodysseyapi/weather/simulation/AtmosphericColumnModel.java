package com.thunder.wildernessodysseyapi.weather.simulation;

import com.thunder.wildernessodysseyapi.weather.api.WindVector;

/** Evolves four coarse temperature/wind levels; humidity profiles diagnose the shared column inventory. */
public final class AtmosphericColumnModel {
    private AtmosphericColumnModel() { }

    public static AtmosphericColumn advance(AtmosphericPhysicalState state,
            AtmosphereSimulationEngine.PhysicalNeighborhood neighbors, AtmosphereEnvironment environment,
            double temperature, double pressure, double humidity, double seconds, double cellSizeMeters,
            SimulationSettings controls) {
        AtmosphericLayer[] layers = new AtmosphericLayer[4];
        for (int i = 0; i < 4; i++) {
            final int index = i;
            AtmosphericLayer previous = state.column().layer(i);
            double layerTemperature = temperature;
            if (i > 0) {
                layerTemperature = previous.temperatureCelsius() + AtmosphericTransport.intensiveDelta(state, neighbors,
                        value -> value.column().layer(index).temperatureCelsius(), index, seconds, cellSizeMeters,
                        Math.min(1.0, controls.temperatureTransportRate() / 0.10));
                double target = temperature - previous.heightMetres() * 0.0065;
                layerTemperature += (target - layerTemperature) * AtmosphericUnits.response(seconds, 7200);
            }
            WindVector wind = WindPhysicsModel.advance(previous,
                    neighbors.west().column().layer(i).pressureHpa(), neighbors.east().column().layer(i).pressureHpa(),
                    neighbors.north().column().layer(i).pressureHpa(), neighbors.south().column().layer(i).pressureHpa(),
                    cellSizeMeters, seconds, environment.terrainRoughness(), controls.coriolisPerSecond(),
                    controls.pressureEqualizationRate() / 0.20);
            double profileHumidity = i == 0 ? humidity : AtmosphericUnits.unit(humidity
                    * previous.relativeHumidity() / Math.max(0.1, state.column().surface().relativeHumidity()));
            // Hydrostatic scale height responds to the column's mean temperature;
            // horizontally different warm/cold layers create upper pressure gradients and shear.
            double scaleHeight = 287.05 * ((temperature + layerTemperature) * 0.5 + 273.15) / 9.81;
            layers[i] = new AtmosphericLayer(previous.heightMetres(),
                    pressure * Math.exp(-previous.heightMetres() / scaleHeight), layerTemperature,
                    profileHumidity, wind.x(), wind.z());
        }
        return new AtmosphericColumn(layers[0], layers[1], layers[2], layers[3]);
    }
}
