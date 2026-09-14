package com.thunder.wildernessodysseyapi.weather.simulation;

import com.thunder.wildernessodysseyapi.weather.api.SurfaceWeatherState;
import com.thunder.wildernessodysseyapi.weather.api.WindVector;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AtmosphericTransportTest {
    @Test
    void vaporPulseTravelsTheSamePhysicalDistanceAtDifferentGridResolutions() {
        for (int width : new int[]{128, 256, 512}) {
            AtmosphericPhysicalState[] cells = new AtmosphericPhysicalState[81];
            for (int i = 0; i < cells.length; i++) cells[i] = state(i == 40 ? 1 : 0);
            for (int step = 0; step < 120; step++) {
                AtmosphericPhysicalState[] next = new AtmosphericPhysicalState[cells.length];
                for (int i = 0; i < cells.length; i++) {
                    var neighbors = AtmosphereSimulationEngine.PhysicalNeighborhood.bounded(cells[i], null,
                            i + 1 < cells.length ? cells[i + 1] : null, null, i > 0 ? cells[i - 1] : null);
                    double moved = AtmosphericTransport.delta(cells[i], neighbors,
                            AtmosphericPhysicalState::vaporKgPerSquareMetre, 0, .5, width, 1);
                    next[i] = state(cells[i].vaporKgPerSquareMetre() + moved);
                }
                cells = next;
            }
            double total = 0, moment = 0;
            for (int i = 0; i < cells.length; i++) {
                total += cells[i].vaporKgPerSquareMetre();
                moment += cells[i].vaporKgPerSquareMetre() * (i - 40) * width;
            }
            assertEquals(1, total, 1.0E-12);
            assertEquals(600, moment / total, 1.0E-8, "10 m/s for 60 seconds at width " + width);
        }
    }

    private static AtmosphericPhysicalState state(double vapor) {
        var wind = new WindVector(10, 0);
        return new AtmosphericPhysicalState(15, 1013.25, vapor, 0, 0,
                AtmosphericColumn.initial(15, 1013.25, .5, wind, wind), 0, 0, 0, 15, 0, SurfaceWeatherState.DRY);
    }
}
