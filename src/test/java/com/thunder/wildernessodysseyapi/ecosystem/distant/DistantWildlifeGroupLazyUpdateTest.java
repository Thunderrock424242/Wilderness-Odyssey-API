package com.thunder.wildernessodysseyapi.ecosystem.distant;

import com.thunder.wildernessodysseyapi.ecosystem.simulation.AbstractEcosystemModel;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Verifies movement re-anchors do not erase dormant population chronology. */
class DistantWildlifeGroupLazyUpdateTest {

    @Test
    void lazyPopulationUpdateUsesPersistedElapsedTimeAndKeepsGroupBounded() {
        DistantWildlifeGroup original = group();
        long updateTime = AbstractEcosystemModel.TICKS_PER_DAY * 30;

        DistantWildlifeGroup updated = original.withLazyPopulationUpdate(
                new AbstractEcosystemModel.Environment(0.9, 0.9, 0.0, 0.0, 0.0),
                updateTime
        );

        assertTrue(updated.populationEstimate() > original.populationEstimate());
        assertTrue(updated.populationEstimate() <= DistantWildlifeGroup.MAXIMUM_GROUP_POPULATION);
        assertEquals(updateTime, updated.populationReferenceGameTime());
    }

    @Test
    void motionUpdatePreservesPopulationEnvironmentReference() {
        DistantWildlifeGroup original = group().withLazyPopulationUpdate(
                AbstractEcosystemModel.Environment.NEUTRAL,
                AbstractEcosystemModel.TICKS_PER_DAY
        );

        DistantWildlifeGroup moved = original.withMotion(
                original.positionAt(AbstractEcosystemModel.TICKS_PER_DAY),
                0.0,
                1.0,
                0.5,
                AbstractEcosystemModel.TICKS_PER_DAY + 100
        );

        assertEquals(original.populationReferenceGameTime(), moved.populationReferenceGameTime());
        assertEquals(original.populationRemainder(), moved.populationRemainder());
        assertEquals(original.foodAvailability(), moved.foodAvailability());
        assertEquals(original.waterAvailability(), moved.waterAvailability());
    }

    private static DistantWildlifeGroup group() {
        return new DistantWildlifeGroup(
                1L,
                ResourceLocation.withDefaultNamespace("cow"),
                20,
                0.0, 64.0, 0.0,
                1.0, 0.0,
                0.4, 1.0,
                42L, 0L,
                DistantWildlifeForm.GROUND,
                false,
                true
        );
    }
}
