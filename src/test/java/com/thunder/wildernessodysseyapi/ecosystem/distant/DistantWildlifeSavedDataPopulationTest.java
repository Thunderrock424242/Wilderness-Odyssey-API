package com.thunder.wildernessodysseyapi.ecosystem.distant;

import com.thunder.wildernessodysseyapi.ecosystem.simulation.AbstractEcosystemModel;
import com.thunder.wildernessodysseyapi.ecosystem.simulation.EcosystemCellKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Verifies server-thread authority checks and hard caps for ecology results. */
class DistantWildlifeSavedDataPopulationTest {

    @Test
    void regionSnapshotUsesKnownGroupsWithoutWorldOrChunkDiscovery() {
        DistantWildlifeSavedData data = dataWithPopulation(4);

        assertEquals(1, data.groupsInRegion(new EcosystemCellKey(0, 0), 64, 0L).size());
        assertTrue(data.groupsInRegion(new EcosystemCellKey(2, 0), 64, 0L).isEmpty());
    }

    @Test
    void applyCapsGrowthAtDimensionMaximumAndPreservesCurrentMotion() {
        DistantWildlifeSavedData data = dataWithPopulation(4);
        DistantWildlifeGroup before = data.groups().getFirst();
        DistantWildlifeGroup moved = before.withMotion(new Vec3(4.0, 64.0, 4.0), 0.0, 1.0, 0.5, 100L);
        assertTrue(data.replace(moved));
        DistantWildlifeGroup calculated = before.withPopulationEcologyState(
                10,
                0.25,
                24_000L,
                new AbstractEcosystemModel.Environment(0.8, 0.8, 0.1, 0.0, 0.0)
        );
        DistantWildlifePopulationUpdate update = DistantWildlifePopulationUpdate.between(before, calculated);

        DistantWildlifeSavedData.PopulationApplyResult result = data.applyPopulationUpdates(List.of(update), 6);
        DistantWildlifeGroup applied = data.groups().getFirst();

        assertEquals(1, result.appliedGroups());
        assertEquals(2, result.animalsAdded());
        assertEquals(6, applied.populationEstimate());
        assertEquals(0.0, applied.populationRemainder());
        assertEquals(moved.anchorX(), applied.anchorX());
        assertEquals(moved.directionZ(), applied.directionZ());
        assertEquals(24_000L, applied.populationReferenceGameTime());
    }

    @Test
    void stalePopulationResultCannotOverwriteMaterialization() {
        DistantWildlifeSavedData data = dataWithPopulation(4);
        DistantWildlifeGroup before = data.groups().getFirst();
        DistantWildlifeGroup calculated = before.withPopulationEcologyState(
                5,
                0.0,
                24_000L,
                AbstractEcosystemModel.Environment.NEUTRAL
        );
        DistantWildlifePopulationUpdate update = DistantWildlifePopulationUpdate.between(before, calculated);
        assertTrue(data.materializedOne(before.id()));

        assertFalse(data.hasCurrentPopulationUpdate(List.of(update)));
        DistantWildlifeSavedData.PopulationApplyResult result = data.applyPopulationUpdates(List.of(update), 512);

        assertEquals(0, result.appliedGroups());
        assertEquals(1, result.staleGroups());
        assertEquals(3, data.groups().getFirst().populationEstimate());
    }

    @Test
    void declinesFreeCapacityBeforeDeterministicGrowth() {
        DistantWildlifeSavedData data = new DistantWildlifeSavedData();
        addPopulation(data, ResourceLocation.withDefaultNamespace("cow"), 4);
        addPopulation(data, ResourceLocation.withDefaultNamespace("pig"), 4);
        DistantWildlifeGroup cow = group(data, "cow");
        DistantWildlifeGroup pig = group(data, "pig");
        DistantWildlifePopulationUpdate cowDecline = DistantWildlifePopulationUpdate.between(
                cow,
                cow.withPopulationEcologyState(2, 0.0, 24_000L, AbstractEcosystemModel.Environment.NEUTRAL)
        );
        DistantWildlifePopulationUpdate pigGrowth = DistantWildlifePopulationUpdate.between(
                pig,
                pig.withPopulationEcologyState(8, 0.0, 24_000L, AbstractEcosystemModel.Environment.NEUTRAL)
        );

        DistantWildlifeSavedData.PopulationApplyResult result = data.applyPopulationUpdates(
                List.of(pigGrowth, cowDecline),
                10
        );

        assertEquals(2, result.appliedGroups());
        assertEquals(4, result.animalsAdded());
        assertEquals(2, result.animalsRemoved());
        assertEquals(10, data.representedAnimals());
        assertEquals(2, group(data, "cow").populationEstimate());
        assertEquals(8, group(data, "pig").populationEstimate());
    }

    private static DistantWildlifeSavedData dataWithPopulation(int population) {
        DistantWildlifeSavedData data = new DistantWildlifeSavedData();
        addPopulation(data, ResourceLocation.withDefaultNamespace("cow"), population);
        return data;
    }

    private static void addPopulation(
            DistantWildlifeSavedData data,
            ResourceLocation species,
            int population
    ) {
        for (int index = 0; index < population; index++) {
            assertTrue(data.absorb(
                    species,
                    new Vec3(0.0, 64.0, 0.0),
                    1.0,
                    0.0,
                    0.4,
                    10L + index,
                    0L,
                    DistantWildlifeForm.GROUND,
                    false,
                    true,
                    64,
                    512
            ));
        }
    }

    private static DistantWildlifeGroup group(DistantWildlifeSavedData data, String speciesPath) {
        return data.groups().stream()
                .filter(group -> group.species().equals(ResourceLocation.withDefaultNamespace(speciesPath)))
                .findFirst()
                .orElseThrow();
    }
}
