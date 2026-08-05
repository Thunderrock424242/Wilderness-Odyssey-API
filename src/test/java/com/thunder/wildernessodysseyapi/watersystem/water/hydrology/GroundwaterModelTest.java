package com.thunder.wildernessodysseyapi.watersystem.water.hydrology;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Verifies recharge, storage, baseflow, and the disabled fallback. */
class GroundwaterModelTest {

    @Test
    void sustainedInfiltrationRechargesAquiferStorage() {
        GroundwaterModel.Result result = GroundwaterModel.advance(new GroundwaterModel.Input(
                0.0f, 0.22f, 1.0f, 0.2f, 0.35f, 0.25f,
                0.40f, 0.018f, 0.78f, false, true
        ));

        assertTrue(result.recharge() > 0.0f);
        assertTrue(result.storage() > 0.22f);
        assertEquals(0.0f, result.discharge());
    }

    @Test
    void connectedAquiferReleasesDelayedBaseflowInDryWeather() {
        GroundwaterModel.Result result = GroundwaterModel.advance(new GroundwaterModel.Input(
                0.08f, 0.84f, 0.0f, 0.0f, 0.45f, 0.75f,
                0.32f, 0.025f, 0.78f, true, true
        ));

        assertTrue(result.discharge() > 0.0f);
        assertTrue(result.storage() < 0.84f);
        assertTrue(result.recharge() < 0.08f);
    }

    @Test
    void disabledGroundwaterPreservesStorageWithoutProducingBaseflow() {
        GroundwaterModel.Result result = GroundwaterModel.advance(new GroundwaterModel.Input(
                0.4f, 0.9f, 1.0f, 1.0f, 1.0f, 1.0f,
                1.0f, 1.0f, 0.5f, true, false
        ));

        assertEquals(0.4f, result.recharge());
        assertEquals(0.9f, result.storage());
        assertEquals(0.0f, result.discharge());
    }
}
