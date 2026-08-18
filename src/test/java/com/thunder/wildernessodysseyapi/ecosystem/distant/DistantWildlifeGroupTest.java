package com.thunder.wildernessodysseyapi.ecosystem.distant;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

/** Verifies deterministic group movement and population-only absorption. */
class DistantWildlifeGroupTest {

    @Test
    void positionUsesNormalizedDirectionAndElapsedServerTime() {
        DistantWildlifeGroup group = group(3, 0.6, 3.0, 4.0);

        Vec3 position = group.positionAt(140.0);

        assertEquals(10.72, position.x, 1.0E-9);
        assertEquals(70.0, position.y, 1.0E-9);
        assertEquals(-3.04, position.z, 1.0E-9);
        assertEquals(position, group.positionAt(140.0));
    }

    @Test
    void absorbIncrementsPopulationWithoutCreatingPerAnimalState() {
        DistantWildlifeGroup original = group(3, 0.6, 1.0, 0.0);

        DistantWildlifeGroup absorbed = original.absorb(new Vec3(16.0, 70.0, -4.0), 1.0, 100L);

        assertEquals(3, original.populationEstimate());
        assertEquals(4, absorbed.populationEstimate());
        assertEquals(11.5, absorbed.anchorX(), 1.0E-9);
        assertEquals(0.7, absorbed.cruiseSpeed(), 1.0E-9);
        assertNotEquals(original, absorbed);
    }

    @Test
    void motionReanchorIsContinuousAtSnapshotTime() {
        DistantWildlifeGroup original = group(3, 0.6, 3.0, 4.0);
        Vec3 current = original.positionAt(140.0);

        DistantWildlifeGroup reanchored = original.withMotion(
                current,
                original.directionX(),
                original.directionZ(),
                original.activityScale(),
                140L
        );

        assertEquals(current, reanchored.positionAt(140.0));
        assertEquals(current.x + 0.18, reanchored.positionAt(150.0).x, 1.0E-9);
        assertEquals(current.z + 0.24, reanchored.positionAt(150.0).z, 1.0E-9);
    }

    private static DistantWildlifeGroup group(
            int population,
            double cruiseSpeed,
            double directionX,
            double directionZ
    ) {
        return new DistantWildlifeGroup(
                7L,
                ResourceLocation.fromNamespaceAndPath("examplemod", "deer"),
                population,
                10.0,
                70.0,
                -4.0,
                directionX,
                directionZ,
                cruiseSpeed,
                1.0,
                91L,
                100L,
                DistantWildlifeForm.GROUND,
                false,
                true
        );
    }
}
