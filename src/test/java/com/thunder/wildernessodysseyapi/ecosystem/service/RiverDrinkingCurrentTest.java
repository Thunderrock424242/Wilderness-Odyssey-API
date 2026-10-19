package com.thunder.wildernessodysseyapi.ecosystem.service;

import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class RiverDrinkingCurrentTest {
    @Test
    void composedPhysicalCurrentUsesTheExistingBlocksPerTickSafetyScale() {
        assertEquals(0.06f, CachedWaterSourceLocator.currentStrengthInTicks(new Vec3(1.2, 0, 0)), 0.00001f);
        assertEquals(0.05f, CachedWaterSourceLocator.currentStrengthInTicks(new Vec3(0.6, -8, 0.8)), 0.00001f);
        assertEquals(0, CachedWaterSourceLocator.currentStrengthInTicks(Vec3.ZERO));
    }

    @Test
    void invalidPhysicalSamplesFailClosedForBankSafety() {
        assertEquals(Float.POSITIVE_INFINITY,
                CachedWaterSourceLocator.currentStrengthInTicks(new Vec3(Double.NaN, 0, 0)));
    }
}
