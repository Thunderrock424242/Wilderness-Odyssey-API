package com.thunder.wildernessodysseyapi.meteor.worldgen;

import com.thunder.wildernessodysseyapi.temporalrift.echo.structure.EchoStructurePolicy;
import com.thunder.wildernessodysseyapi.temporalrift.registry.TemporalRiftDimensions;
import com.thunder.wildernessodysseyapi.anomaly.registry.AnomalyDimensions;
import com.mojang.serialization.Lifecycle;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.MappedRegistry;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.GenerationStep;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureSet;
import net.minecraft.world.level.levelgen.structure.TerrainAdjustment;
import net.minecraft.world.level.levelgen.structure.placement.RandomSpreadStructurePlacement;
import net.minecraft.world.level.levelgen.structure.placement.RandomSpreadType;
import net.minecraft.world.level.levelgen.structure.structures.BuriedTreasureStructure;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertSame;

class MeteorDimensionPolicyTest {
    @Test
    void registeredNaturalCraterSetIsRemovedFromEchoGenerationCandidates() {
        var id = ResourceLocation.fromNamespaceAndPath("wildernessodysseyapi", "meteor_craters");
        var rules = EchoStructurePolicy.snapshot();

        assertTrue(EchoStructurePolicy.resolve(id, true, rules, ignored -> true).isEmpty());
        assertEquals(Optional.of(id), EchoStructurePolicy.resolve(id, false, rules, ignored -> true));
    }

    @Test
    void inertAndNonEarthDimensionsExcludeOnlyTheNaturalSetAndRetainDirectLookupIdentity() {
        MappedRegistry<StructureSet> sets = new MappedRegistry<>(Registries.STRUCTURE_SET, Lifecycle.stable());
        var craterKey = ResourceKey.create(Registries.STRUCTURE_SET, MeteorWorldgenRegistries.NATURAL_CRATER_SET);
        var otherKey = ResourceKey.create(Registries.STRUCTURE_SET,
                ResourceLocation.fromNamespaceAndPath("minecraft", "buried_treasures"));
        var treasure = new BuriedTreasureStructure(new Structure.StructureSettings(HolderSet.direct(List.of()),
                Map.of(), GenerationStep.Decoration.SURFACE_STRUCTURES, TerrainAdjustment.NONE));
        var value = new StructureSet(Holder.direct(treasure),
                new RandomSpreadStructurePlacement(28, 17, RandomSpreadType.LINEAR, 43_719_873));
        Registry.register(sets, craterKey, value);
        Registry.register(sets, otherKey, new StructureSet(Holder.direct(treasure),
                new RandomSpreadStructurePlacement(28, 17, RandomSpreadType.LINEAR, 43_719_874)));
        var lookup = sets.asLookup();

        assertSame(lookup, MeteorWorldgenRegistries.forDimension(lookup, Level.OVERWORLD));
        for (var dimension : List.of(TemporalRiftDimensions.THE_BEFORE_KEY, TemporalRiftDimensions.THE_ECHO_KEY,
                AnomalyDimensions.ANOMALY_DIMENSION_KEY, Level.NETHER)) {
            var filtered = MeteorWorldgenRegistries.forDimension(lookup, dimension);
            assertEquals(List.of(otherKey), filtered.listElements().map(Holder.Reference::key).toList());
            assertSame(lookup.get(craterKey).orElseThrow(), filtered.get(craterKey).orElseThrow());
            assertSame(lookup.get(otherKey).orElseThrow(), filtered.get(otherKey).orElseThrow());
        }
    }
}
