package com.thunder.wildernessodysseyapi.ecosystem.data;

import com.thunder.wildernessodysseyapi.ecosystem.EcosystemTags;
import com.thunder.wildernessodysseyapi.ecosystem.api.AnimalBehaviorTag;
import com.thunder.wildernessodysseyapi.ecosystem.api.SpeciesBehaviorProfile;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Verifies config archetypes expand into complete safe runtime profiles. */
class BehaviorTagProfileFactoryTest {

    @Test
    void herbivoreArchetypeEnablesNeedsHerdShelterAndPreyResponse() {
        SpeciesBehaviorProfile profile = BehaviorTagProfileFactory.create(
                ResourceLocation.withDefaultNamespace("cow"),
                Set.of(AnimalBehaviorTag.HERBIVORE)
        );

        assertTrue(profile.drinking().enabled());
        assertTrue(profile.shelter().enabled());
        assertTrue(profile.herd().enabled());
        assertTrue(profile.prey().enabled());
        assertFalse(profile.predator().enabled());
        assertFalse(profile.drinking().canSwim());
        assertEquals(EcosystemTags.PREDATORS_ID, profile.prey().threatTags().getFirst());
    }

    @Test
    void wolfArchetypeEnablesPackSwimmingNocturnalAndProtectedHunting() {
        SpeciesBehaviorProfile profile = BehaviorTagProfileFactory.create(
                ResourceLocation.withDefaultNamespace("wolf"),
                Set.of(AnimalBehaviorTag.WOLF)
        );

        assertTrue(profile.herd().enabled());
        assertTrue(profile.drinking().canSwim());
        assertTrue(profile.needs().nocturnal());
        assertTrue(profile.predator().enabled());
        assertFalse(profile.prey().enabled());
        assertEquals(4, profile.predator().minimumNearbyPrey());
        assertEquals(EcosystemTags.WOLF_PREY_ID, profile.predator().preyTags().getFirst());
    }

    @Test
    void solitaryModifierDisablesAutomaticBirdFlocking() {
        SpeciesBehaviorProfile profile = BehaviorTagProfileFactory.create(
                ResourceLocation.fromNamespaceAndPath("examplemod", "owl"),
                Set.of(AnimalBehaviorTag.BIRD, AnimalBehaviorTag.SOLITARY, AnimalBehaviorTag.NOCTURNAL)
        );

        assertFalse(profile.herd().enabled());
        assertTrue(profile.prey().enabled());
        assertTrue(profile.needs().nocturnal());
        assertEquals(45, profile.drinking().durationTicks());
    }

    @Test
    void genericPredatorUsesExtensibleGenericPreyTag() {
        SpeciesBehaviorProfile profile = BehaviorTagProfileFactory.create(
                ResourceLocation.fromNamespaceAndPath("examplemod", "lynx"),
                Set.of(AnimalBehaviorTag.PREDATOR, AnimalBehaviorTag.SOLITARY)
        );

        assertTrue(profile.predator().enabled());
        assertFalse(profile.herd().enabled());
        assertEquals(EcosystemTags.PREY_ID, profile.predator().preyTags().getFirst());
    }

    @Test
    void genericAnimalIsSafePreyWithoutAssumedHerdingOrHunting() {
        SpeciesBehaviorProfile profile = BehaviorTagProfileFactory.createAutoDetected(
                ResourceLocation.fromNamespaceAndPath("examplemod", "deer_like_animal"),
                Set.of(AnimalBehaviorTag.ANIMAL)
        );

        assertEquals("detected/examplemod/deer_like_animal", profile.id().getPath());
        assertTrue(profile.drinking().enabled());
        assertTrue(profile.shelter().enabled());
        assertTrue(profile.prey().enabled());
        assertFalse(profile.herd().enabled());
        assertFalse(profile.predator().enabled());
    }

    @Test
    void aquaticAnimalDoesNotSeekDrinkingWaterOrWeatherShelter() {
        SpeciesBehaviorProfile profile = BehaviorTagProfileFactory.createAutoDetected(
                ResourceLocation.fromNamespaceAndPath("examplemod", "river_fish"),
                Set.of(AnimalBehaviorTag.AQUATIC, AnimalBehaviorTag.FLOCK)
        );

        assertFalse(profile.drinking().enabled());
        assertFalse(profile.shelter().enabled());
        assertTrue(profile.drinking().canSwim());
        assertTrue(profile.herd().enabled());
        assertTrue(profile.prey().enabled());
    }
}
