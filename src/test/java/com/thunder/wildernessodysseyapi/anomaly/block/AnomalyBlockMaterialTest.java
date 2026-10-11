package com.thunder.wildernessodysseyapi.anomaly.block;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AnomalyBlockMaterialTest {
    @Test
    void disabledOrServerAnimationNeverEmitsCrackMotes() {
        for (AnomalyBlockMaterial material : AnomalyBlockMaterial.values()) {
            assertFalse(material.shouldEmitCrackMote(false, true, 0));
            assertFalse(material.shouldEmitCrackMote(true, false, 0));
            assertFalse(material.shouldEmitCrackMote(false, false, 0));
        }
    }

    @Test
    void eachMaterialEmitsOnlyOnceAcrossACompleteSampleCycle() {
        for (AnomalyBlockMaterial material : AnomalyBlockMaterial.values()) {
            int motes = 0;
            for (int sample = 0; sample < material.moteSampleBound(); sample++) {
                if (material.shouldEmitCrackMote(true, true, sample)) {
                    motes++;
                }
            }
            assertEquals(1, motes, "Cracks should emit one restrained mote per sample cycle");
            assertTrue((double) motes / material.moteSampleBound() <= 0.01D,
                    "Nearby terrain must emit on at most one percent of animation samples");
            assertFalse(material.shouldEmitCrackMote(true, true, -1));
            assertFalse(material.shouldEmitCrackMote(true, true, material.moteSampleBound()));
        }
    }
}
