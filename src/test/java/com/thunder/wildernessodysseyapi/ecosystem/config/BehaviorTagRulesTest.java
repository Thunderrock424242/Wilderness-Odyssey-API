package com.thunder.wildernessodysseyapi.ecosystem.config;

import com.thunder.wildernessodysseyapi.ecosystem.api.AnimalBehaviorTag;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Verifies friendly config parsing and exact-over-entity-tag precedence. */
class BehaviorTagRulesTest {

    @Test
    void exactEntityAssignmentWinsOverMatchingEntityTypeTag() {
        BehaviorTagRules rules = BehaviorTagRules.parse(List.of(
                "#c:animals/birds=birds,flocks",
                "minecraft:chicken=herbivores,herd"
        ));

        Set<AnimalBehaviorTag> resolved = rules.resolve(
                ResourceLocation.withDefaultNamespace("chicken"),
                tag -> tag.equals(ResourceLocation.fromNamespaceAndPath("c", "animals/birds"))
        ).orElseThrow();

        assertEquals(Set.of(AnimalBehaviorTag.HERBIVORE, AnimalBehaviorTag.HERD), resolved);
    }

    @Test
    void entityTypeTagCanAssignOneArchetypeToModdedAnimals() {
        BehaviorTagRules rules = BehaviorTagRules.parse(List.of(
                "#examplemod:animals/forest_birds=bird,flock,prey"
        ));

        Set<AnimalBehaviorTag> resolved = rules.resolve(
                ResourceLocation.fromNamespaceAndPath("examplemod", "blue_jay"),
                tag -> tag.equals(ResourceLocation.fromNamespaceAndPath("examplemod", "animals/forest_birds"))
        ).orElseThrow();

        assertTrue(resolved.contains(AnimalBehaviorTag.BIRD));
        assertTrue(resolved.contains(AnimalBehaviorTag.FLOCK));
        assertTrue(resolved.contains(AnimalBehaviorTag.PREY));
        assertEquals("#examplemod:animals/forest_birds", rules.rules().getFirst().selectorExpression());
    }

    @Test
    void rejectsUnknownBehaviorLabelsAndMalformedSelectors() {
        assertFalse(BehaviorTagRules.isValid("minecraft:cow=unknown_behavior"));
        assertFalse(BehaviorTagRules.isValid("not an id=herbivore"));
        assertFalse(BehaviorTagRules.isValid("minecraft:cow"));
        assertFalse(BehaviorTagRules.isValid("examplemod:boss=disabled,predator"));
        assertTrue(BehaviorTagRules.isValid("minecraft:wolf=wolves"));
        assertTrue(BehaviorTagRules.isValid("examplemod:boss=ignore"));
    }
}
