package com.thunder.wildernessodysseyapi.ecosystem.behavior;

import com.thunder.wildernessodysseyapi.ecosystem.api.SpeciesBehaviorProfile;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** Verifies bounded deterministic need integration without a loaded Minecraft world. */
class EcosystemNeedModelTest {

    private static final SpeciesBehaviorProfile.Needs PROFILE = new SpeciesBehaviorProfile.Needs(
            0.10,
            0.10,
            0.10,
            28.0,
            2.0,
            1.5,
            false
    );

    @Test
    void heatAndMovementIncreaseThirstWhileForageOnlySlowsHunger() {
        EcosystemNeedModel.Values values = EcosystemNeedModel.advance(
                new EcosystemNeedModel.Values(0.0, 0.0, 0.0, 0.0, 0.0),
                PROFILE,
                1_200,
                40.0,
                true,
                true,
                false,
                1.0,
                false,
                true,
                1.0,
                1.0
        );

        assertEquals(0.30, values.thirst(), 1.0E-9);
        assertEquals(0.055, values.hunger(), 1.0E-9);
        assertEquals(0.10, values.rest(), 1.0E-9);
        assertEquals(0.06, values.social(), 1.0E-9);
        assertEquals(1.0, values.safetyConcern(), 1.0E-9);
    }

    @Test
    void shelterAndHerdCanRecoverRestAndSocialWithoutLeavingUnitRange() {
        EcosystemNeedModel.Values values = EcosystemNeedModel.advance(
                new EcosystemNeedModel.Values(0.95, 0.95, 0.1, 0.1, 0.1),
                PROFILE,
                24_000,
                15.0,
                false,
                false,
                true,
                0.0,
                true,
                false,
                10.0,
                10.0
        );

        assertEquals(1.0, values.thirst(), 1.0E-9);
        assertEquals(1.0, values.hunger(), 1.0E-9);
        assertEquals(0.0, values.rest(), 1.0E-9);
        assertEquals(0.0, values.social(), 1.0E-9);
        assertEquals(0.0, values.safetyConcern(), 1.0E-9);
    }
}
