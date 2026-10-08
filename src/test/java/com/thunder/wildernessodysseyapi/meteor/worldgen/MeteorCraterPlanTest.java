package com.thunder.wildernessodysseyapi.meteor.worldgen;

import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MeteorCraterPlanTest {

    @Test
    void seededPlansAreRepeatableIncludingUndergroundParameters() {
        BlockPos center = new BlockPos(-24, 82, 40);
        assertEquals(MeteorCraterPlan.create(center, RandomSource.create(8675309L)),
                MeteorCraterPlan.create(center, RandomSource.create(8675309L)));
    }

    @Test
    void largestCraterRetainsItsBowlAndFitsTheEightChunkReferenceRing() {
        MeteorCraterPlan plan = new MeteorCraterPlan(new BlockPos(-24, 82, 40),
                125, 32, 20, 69, 1L);
        var bounds = plan.bounds(-64, 319);
        int centerChunkX = plan.center().getX() >> 4;
        int centerChunkZ = plan.center().getZ() >> 4;

        assertEquals(125, plan.radius());
        assertTrue(plan.footprintRadius() >= plan.radius());
        assertTrue(Math.abs((bounds.minX() >> 4) - centerChunkX) <= 8);
        assertTrue(Math.abs((bounds.maxX() >> 4) - centerChunkX) <= 8);
        assertTrue(Math.abs((bounds.minZ() >> 4) - centerChunkZ) <= 8);
        assertTrue(Math.abs((bounds.maxZ() >> 4) - centerChunkZ) <= 8);
    }
}
