package com.thunder.wildernessodysseyapi.watersystem.ocean.coast;

import com.thunder.wildernessodysseyapi.watersystem.ocean.OceanSeaState;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** Checks whitewater lifetime independently of graphics and particle timing. */
class CoastalFoamModelTest {
    @Test
    void calmBreakingSwellHasReadableFoam() {
        var wave = at(0.50f);
        assertEquals(CoastalWaveModel.Stage.BREAKING, wave.stage());
        assertTrue(wave.foam() > 0.25f);
    }

    @Test
    void trailAppearsAfterBreakingAndFadesBeforeNextCycle() {
        assertEquals(0.0f, CoastalFoamModel.trail(at(0.10f), 0.0f));
        assertTrue(CoastalFoamModel.trail(at(0.70f), 0.0f) > 0.2f);
        assertTrue(CoastalFoamModel.trail(at(0.90f), 0.0f)
                > CoastalFoamModel.trail(at(0.99f), 0.0f));
        assertEquals(0.0f, CoastalFoamModel.trail(at(0.70f), 100.0f));
        assertEquals(0.0f, CoastalFoamModel.trail(at(0.70f), Float.NaN));
    }

    @Test
    void washFrontIsWhiterThanTheInterior() {
        assertTrue(CoastalFoamModel.wash(4, 4, 12, 13, 0.7f)
                > CoastalFoamModel.wash(1, 4, 12, 13, 0.7f));
    }

    private static CoastalWaveModel.Sample at(float phase) {
        return CoastalWaveModel.sampleAtPhase(1L, phase, 8.0f,
                CoastalWaveProfile.TEMPERATE, OceanSeaState.CALM, 0.1f, 1.0f);
    }
}
