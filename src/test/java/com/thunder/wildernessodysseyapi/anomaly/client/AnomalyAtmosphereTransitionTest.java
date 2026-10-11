package com.thunder.wildernessodysseyapi.anomaly.client;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AnomalyAtmosphereTransitionTest {
    @Test
    void rejectsPacketsForAnotherDimension() {
        AnomalyAtmosphereTransition state = new AnomalyAtmosphereTransition();
        assertFalse(state.accept("minecraft:overworld", "wildernessodysseyapi:anomaly_dimension", 4, 1.0F, 1.0F));
        assertEquals(0.0F, state.intensity());
        assertEquals(-1, state.zone());
    }

    @Test
    void approachesNewStrengthGradually() {
        AnomalyAtmosphereTransition state = new AnomalyAtmosphereTransition();
        assertTrue(state.accept("minecraft:overworld", "minecraft:overworld", 0, 1.0F, 0.5F));
        state.tick("minecraft:overworld");
        assertTrue(state.intensity() > 0.0F && state.intensity() < 1.0F);
        for (int tick = 0; tick < 100; tick++) {
            state.tick("minecraft:overworld");
        }
        assertEquals(1.0F, state.intensity(), 0.001F);
        assertEquals(0.5F, state.riftIntensity(), 0.001F);
    }

    @Test
    void clearsImmediatelyWhenTheDimensionChanges() {
        AnomalyAtmosphereTransition state = new AnomalyAtmosphereTransition();
        state.accept("minecraft:overworld", "minecraft:overworld", 0, 1.0F, 1.0F);
        state.tick("minecraft:overworld");
        state.tick("minecraft:the_nether");
        assertEquals(0.0F, state.intensity());
        assertEquals(0.0F, state.riftIntensity());
        assertEquals(-1, state.zone());
    }

    @Test
    void fadesExpiredPacketsAfterTheRefreshWindow() {
        AnomalyAtmosphereTransition state = new AnomalyAtmosphereTransition();
        state.accept("minecraft:overworld", "minecraft:overworld", 4, 1.0F, 1.0F);
        for (int tick = 0; tick < 450; tick++) {
            state.tick("minecraft:overworld");
        }
        assertEquals(0.0F, state.intensity(), 0.001F);
        assertEquals(0.0F, state.riftIntensity(), 0.001F);
        assertEquals(-1, state.zone());
    }

    @Test
    void refreshedStrengthDoesNotExpire() {
        AnomalyAtmosphereTransition state = new AnomalyAtmosphereTransition();
        for (int tick = 0; tick < 1_000; tick++) {
            if (tick % 200 == 0) {
                state.accept("minecraft:overworld", "minecraft:overworld", 2, 0.5F, 0.0F);
            }
            state.tick("minecraft:overworld");
        }
        assertEquals(0.5F, state.intensity(), 0.001F);
        assertEquals(2, state.zone());
    }
}
