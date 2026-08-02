package com.thunder.wildernessodysseyapi.watersystem.water.compat.vanilla;

import com.thunder.wildernessodysseyapi.watersystem.water.api.WaterSample;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EntityWaterContactSamplerTest {

    @Test
    void footprintSamplesCornersAndClampsLeadingPointToBow() {
        AABB bounds = new AABB(0.0, 4.0, 10.0, 4.0, 5.0, 12.0);

        EntityWaterContactSampler.Footprint footprint =
                EntityWaterContactSampler.footprint(bounds, new Vec3(100.0, 0.0, 0.0));

        assertEquals(0.18, footprint.minimumX(), 1.0e-12);
        assertEquals(3.82, footprint.maximumX(), 1.0e-12);
        assertEquals(10.18, footprint.minimumZ(), 1.0e-12);
        assertEquals(11.82, footprint.maximumZ(), 1.0e-12);
        assertEquals(footprint.maximumX(), footprint.leadingX(), 1.0e-12);
        assertEquals(11.0, footprint.leadingZ(), 1.0e-12);
    }

    @Test
    void oneWetCornerMakesWideEntityTouchingWithoutClaimingFullSubmersion() {
        EntityWaterContactSampler.ContactAccumulator accumulator =
                new EntityWaterContactSampler.ContactAccumulator()
                        .reset(new AABB(0.0, 0.0, 0.0, 4.0, 1.0, 2.0));
        WaterSample dry = new WaterSample().clear();
        WaterSample wetCorner = new WaterSample().set(
                true, 0.6, 0.6, 0.4, 0.1, -0.2, 0.0, 1.0, 0.0
        );

        accumulator.addBody(dry);
        accumulator.addBody(dry);
        accumulator.addBody(dry);
        accumulator.addBody(wetCorner);
        accumulator.addBody(dry);
        accumulator.addEye(dry, 0.9);

        assertTrue(accumulator.authoritativeContactKnown());
        assertTrue(accumulator.touchingWater());
        assertFalse(accumulator.bodySubmerged());
        assertFalse(accumulator.eyesSubmerged());
        assertEquals(0.4, accumulator.currentX(), 1.0e-12);
    }

    @Test
    void allWetFootprintPointsRequireSurfaceAboveWholeBody() {
        EntityWaterContactSampler.ContactAccumulator accumulator =
                new EntityWaterContactSampler.ContactAccumulator()
                        .reset(new AABB(0.0, 0.0, 0.0, 1.0, 1.0, 1.0));
        WaterSample submerged = new WaterSample().set(
                true, 1.25, 1.25, 0.0, 0.0, 0.0, 0.0, 1.0, 0.0
        );

        for (int point = 0; point < 5; point++) {
            accumulator.addBody(submerged);
        }
        accumulator.addEye(submerged, 0.9);

        assertTrue(accumulator.touchingWater());
        assertTrue(accumulator.bodySubmerged());
        assertTrue(accumulator.eyesSubmerged());
        assertEquals(1.25, accumulator.surfaceHeight(), 1.0e-12);
    }

    @Test
    void animatedCrestCanContactAbovePhysicalTopWithoutFloodingDeepDrySpace() {
        EntityWaterContactSampler.ContactAccumulator surfaceAccumulator =
                new EntityWaterContactSampler.ContactAccumulator()
                        .reset(new AABB(0.0, 1.05, 0.0, 1.0, 1.55, 1.0));
        WaterSample crest = new WaterSample().set(
                false, 1.2, 0.0, 0.1, 0.0, 0.0, 0.0, 1.0, 0.0
        );
        surfaceAccumulator.addBody(crest);

        EntityWaterContactSampler.ContactAccumulator deepDryAccumulator =
                new EntityWaterContactSampler.ContactAccumulator()
                        .reset(new AABB(0.0, -8.0, 0.0, 1.0, -7.0, 1.0));
        deepDryAccumulator.addBody(crest);

        assertTrue(surfaceAccumulator.authoritativeContactKnown());
        assertTrue(surfaceAccumulator.touchingWater());
        assertFalse(deepDryAccumulator.authoritativeContactKnown());
        assertFalse(deepDryAccumulator.touchingWater());
    }
}
