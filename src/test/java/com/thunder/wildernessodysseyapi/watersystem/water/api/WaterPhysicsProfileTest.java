package com.thunder.wildernessodysseyapi.watersystem.water.api;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Covers the public profile contract exposed to optional vehicle integrations. */
class WaterPhysicsProfileTest {

    @Test
    void builtInProfilesAndApiVersionArePublished() {
        assertEquals(1, WaterServices.apiVersion());
        assertTrue(WaterPhysicsProfileRegistry.registeredIds().stream()
                .anyMatch(id -> id.getPath().equals("vanilla_boat")));
        assertTrue(WaterPhysicsProfileRegistry.BOAT.rigidWatercraft());
    }

    @Test
    void invalidPhysicalCoefficientsAreRejected() {
        assertThrows(IllegalArgumentException.class, () -> new WaterPhysicsProfile(
                -1.0, 0.0, 0.0,
                1.0,
                0.0, 0.0, 0.0,
                1.0, 1.0, 1.0,
                0.0, 0.0, 0.0,
                1.0,
                false
        ));
    }

    @Test
    void effectiveMassIncludesVolumeAndPayload() {
        WaterPhysicsProfile profile = new WaterPhysicsProfile(
                10.0, 2.0, 0.5,
                1.0,
                0.0, 0.0, 0.0,
                1.0, 1.0, 1.0,
                0.0, 0.0, 0.0,
                1.0,
                false
        );

        assertEquals(17.0, profile.effectiveMass(2.0, 6), 1.0e-12);
    }
}
