package com.thunder.wildernessodysseyapi.ecosystem.data;

import com.thunder.wildernessodysseyapi.ecosystem.api.AnimalBehaviorTag;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Verifies conservative modded-mob inference independently from live entity construction. */
class ModdedMobBehaviorDetectorTest {

    @Test
    void ordinaryAnimalGetsNeutralAnimalBehavior() {
        Set<AnimalBehaviorTag> tags = ModdedMobBehaviorDetector.infer(
                new ModdedMobBehaviorDetector.Traits(
                        ModdedMobBehaviorDetector.KnownFamily.NONE,
                        true, false, false, false, false)
        ).orElseThrow();

        assertEquals(Set.of(AnimalBehaviorTag.ANIMAL), tags);
    }

    @Test
    void flyingPredatorGetsBirdHuntingWithoutAutomaticFlocking() {
        Set<AnimalBehaviorTag> tags = ModdedMobBehaviorDetector.infer(
                new ModdedMobBehaviorDetector.Traits(
                        ModdedMobBehaviorDetector.KnownFamily.NONE,
                        true, true, false, false, true)
        ).orElseThrow();

        assertEquals(Set.of(
                AnimalBehaviorTag.BIRD,
                AnimalBehaviorTag.PREDATOR,
                AnimalBehaviorTag.SOLITARY
        ), tags);
    }

    @Test
    void schoolingWaterAnimalGetsAquaticFlockBehavior() {
        Set<AnimalBehaviorTag> tags = ModdedMobBehaviorDetector.infer(
                new ModdedMobBehaviorDetector.Traits(
                        ModdedMobBehaviorDetector.KnownFamily.NONE,
                        false, false, true, true, false)
        ).orElseThrow();

        assertEquals(Set.of(AnimalBehaviorTag.AQUATIC, AnimalBehaviorTag.FLOCK), tags);
    }

    @Test
    void unrelatedPathfinderMobIsNotAutoDetected() {
        assertTrue(ModdedMobBehaviorDetector.infer(
                new ModdedMobBehaviorDetector.Traits(
                        ModdedMobBehaviorDetector.KnownFamily.NONE,
                        false, false, false, false, true)
        ).isEmpty());
    }

    @Test
    void moddedSubtypeOfVanillaWolfKeepsWolfPackArchetype() {
        Set<AnimalBehaviorTag> tags = ModdedMobBehaviorDetector.infer(
                new ModdedMobBehaviorDetector.Traits(
                        ModdedMobBehaviorDetector.KnownFamily.WOLF,
                        true, false, false, false, true)
        ).orElseThrow();

        assertEquals(Set.of(AnimalBehaviorTag.WOLF), tags);
    }
}
