package com.thunder.wildernessodysseyapi.weather.simulation;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Verifies the bounded thermodynamic approximations used during transport. */
class AtmosphericThermodynamicsTest {

    @Test
    void warmAirCanCarryMoreVaporThanColdAir() {
        assertTrue(
                AtmosphericThermodynamics.saturationCapacity(25.0)
                        > AtmosphericThermodynamics.saturationCapacity(0.0)
        );
    }

    @Test
    void vaporInventoryRoundTripsAtTheSameTemperature() {
        double vapor = AtmosphericThermodynamics.vaporContent(18.0, 0.73);

        assertEquals(
                0.73,
                AtmosphericThermodynamics.relativeHumidity(18.0, vapor),
                1.0E-12
        );
    }

    @Test
    void coolingTheSameVaporRaisesRelativeHumidity() {
        double vapor = AtmosphericThermodynamics.vaporContent(24.0, 0.45);

        assertTrue(AtmosphericThermodynamics.relativeHumidity(8.0, vapor) > 0.45);
    }

    @Test
    void dryAirHasAColderWetBulbTemperature() {
        double humidWetBulb = AtmosphericThermodynamics.wetBulbTemperature(3.0, 0.95);
        double dryWetBulb = AtmosphericThermodynamics.wetBulbTemperature(3.0, 0.30);

        assertTrue(dryWetBulb < humidWetBulb);
    }
}
