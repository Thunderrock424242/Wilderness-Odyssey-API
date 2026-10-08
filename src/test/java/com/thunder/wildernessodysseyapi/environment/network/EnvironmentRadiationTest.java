package com.thunder.wildernessodysseyapi.environment.network;

import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** Radiation exposure remains meaningful independently of nearest-site display metadata. */
class EnvironmentRadiationTest {

    @Test
    void retainsExposureWhenNearestSiteMetadataIsUnavailable() {
        EnvironmentSyncPayload payload = new EnvironmentSyncPayload(
                ResourceLocation.withDefaultNamespace("overworld"), 10L,
                0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.84F,
                0.0F, 0.0F, 0.84F, 0.84F, 0.0F, 0.0F, 0.0F, 0.84F,
                false, false, false, 0, 0, 0, 0, 0);

        assertEquals(0.84F, payload.radiation(), 0.00001F);
        assertEquals(0, payload.meteorRadius());
    }
}
