package com.thunder.wildernessodysseyapi.ecosystem.data;

import com.thunder.wildernessodysseyapi.core.ModConstants;
import com.thunder.wildernessodysseyapi.ecosystem.EcosystemTags;
import com.thunder.wildernessodysseyapi.ecosystem.api.AnimalBehaviorTag;
import com.thunder.wildernessodysseyapi.ecosystem.api.SpeciesBehaviorProfile;
import net.minecraft.resources.ResourceLocation;

import java.util.List;
import java.util.Set;

/**
 * Builds complete runtime species profiles from simple config behavior labels.
 *
 * <p>Archetypes choose conservative biological defaults. Modifier labels then
 * enable or suppress social, prey, swimming, shelter, and activity traits.</p>
 */
public final class BehaviorTagProfileFactory {

    private BehaviorTagProfileFactory() {
    }

    /** Creates one immutable generated profile for an exact runtime entity type. */
    public static SpeciesBehaviorProfile create(
            ResourceLocation entityId,
            Set<AnimalBehaviorTag> behaviorTags
    ) {
        return create(entityId, behaviorTags, "configured");
    }

    /** Creates an immutable profile whose ID identifies runtime auto-detection as its source. */
    public static SpeciesBehaviorProfile createAutoDetected(
            ResourceLocation entityId,
            Set<AnimalBehaviorTag> behaviorTags
    ) {
        return create(entityId, behaviorTags, "detected");
    }

    private static SpeciesBehaviorProfile create(
            ResourceLocation entityId,
            Set<AnimalBehaviorTag> behaviorTags,
            String source
    ) {
        Set<AnimalBehaviorTag> tags = Set.copyOf(behaviorTags);
        if (tags.contains(AnimalBehaviorTag.DISABLED)) {
            throw new IllegalArgumentException("disabled is an exclusion rule, not a behavior profile");
        }
        boolean animal = tags.contains(AnimalBehaviorTag.ANIMAL);
        boolean herbivore = tags.contains(AnimalBehaviorTag.HERBIVORE);
        boolean omnivore = tags.contains(AnimalBehaviorTag.OMNIVORE);
        boolean bird = tags.contains(AnimalBehaviorTag.BIRD);
        boolean wolf = tags.contains(AnimalBehaviorTag.WOLF);
        boolean aquatic = tags.contains(AnimalBehaviorTag.AQUATIC);
        boolean predator = wolf || tags.contains(AnimalBehaviorTag.PREDATOR);
        boolean prey = tags.contains(AnimalBehaviorTag.PREY)
                || (!predator && (animal || herbivore || omnivore || bird || aquatic));
        boolean herd = !tags.contains(AnimalBehaviorTag.SOLITARY)
                && (tags.contains(AnimalBehaviorTag.HERD)
                || tags.contains(AnimalBehaviorTag.FLOCK)
                || tags.contains(AnimalBehaviorTag.PACK)
                || herbivore
                || omnivore
                || bird
                || wolf);
        boolean swimmer = wolf || aquatic || tags.contains(AnimalBehaviorTag.SWIMMER);
        boolean nocturnal = wolf || tags.contains(AnimalBehaviorTag.NOCTURNAL);
        boolean shelter = tags.contains(AnimalBehaviorTag.SHELTER)
                || (!aquatic && (animal || herbivore || omnivore || bird || wolf || predator));

        Archetype archetype = selectArchetype(herbivore, omnivore, bird, wolf, aquatic);
        ResourceLocation profileId = ResourceLocation.fromNamespaceAndPath(
                ModConstants.MOD_ID,
                source + "/" + entityId.getNamespace() + "/" + entityId.getPath()
        );
        return new SpeciesBehaviorProfile(
                profileId,
                Set.of(entityId),
                Set.of(),
                needs(archetype, nocturnal),
                drinking(archetype, swimmer),
                shelter(archetype, shelter),
                herd(archetype, herd, tags),
                prey(archetype, prey),
                predator(archetype, predator, wolf)
        );
    }

    private static Archetype selectArchetype(
            boolean herbivore,
            boolean omnivore,
            boolean bird,
            boolean wolf,
            boolean aquatic
    ) {
        if (wolf) {
            return Archetype.WOLF;
        }
        if (bird) {
            return Archetype.BIRD;
        }
        if (aquatic) {
            return Archetype.AQUATIC;
        }
        if (omnivore) {
            return Archetype.OMNIVORE;
        }
        return herbivore ? Archetype.HERBIVORE : Archetype.GENERIC;
    }

    private static SpeciesBehaviorProfile.Needs needs(Archetype archetype, boolean nocturnal) {
        return switch (archetype) {
            case BIRD -> new SpeciesBehaviorProfile.Needs(0.024, 0.012, 0.007, 28.0, 1.65, 1.20, nocturnal);
            case WOLF -> new SpeciesBehaviorProfile.Needs(0.017, 0.018, 0.006, 27.0, 1.70, 1.45, nocturnal);
            case AQUATIC -> new SpeciesBehaviorProfile.Needs(0.0, 0.010, 0.005, 29.0, 1.0, 1.10, nocturnal);
            case OMNIVORE -> new SpeciesBehaviorProfile.Needs(0.021, 0.010, 0.006, 24.0, 1.90, 1.35, nocturnal);
            case HERBIVORE -> new SpeciesBehaviorProfile.Needs(0.019, 0.009, 0.006, 25.5, 1.80, 1.30, nocturnal);
            case GENERIC -> new SpeciesBehaviorProfile.Needs(0.018, 0.009, 0.006, 27.0, 1.70, 1.25, nocturnal);
        };
    }

    private static SpeciesBehaviorProfile.Drinking drinking(Archetype archetype, boolean swimmer) {
        return switch (archetype) {
            case BIRD -> new SpeciesBehaviorProfile.Drinking(true, 0.55, 18, 45, 1.00, 0.86, swimmer, 0.75);
            case WOLF -> new SpeciesBehaviorProfile.Drinking(true, 0.64, 24, 55, 1.05, 0.90, swimmer, 1.50);
            case AQUATIC -> new SpeciesBehaviorProfile.Drinking(false, 1.0, 8, 20, 1.00, 1.0, true, 8.0);
            case OMNIVORE -> new SpeciesBehaviorProfile.Drinking(true, 0.58, 22, 65, 1.00, 0.88, swimmer, 1.00);
            case HERBIVORE -> new SpeciesBehaviorProfile.Drinking(true, 0.60, 24, 65, 0.98, 0.90, swimmer, 1.00);
            case GENERIC -> new SpeciesBehaviorProfile.Drinking(true, 0.65, 20, 60, 1.00, 0.90, swimmer, 1.00);
        };
    }

    private static SpeciesBehaviorProfile.Shelter shelter(Archetype archetype, boolean enabled) {
        return switch (archetype) {
            case BIRD -> new SpeciesBehaviorProfile.Shelter(enabled, 16, 0.40, 0.28, 0.58, 120, 340, 1.10);
            case WOLF -> new SpeciesBehaviorProfile.Shelter(enabled, 20, 0.72, 0.40, 0.75, 60, 220, 1.10);
            case AQUATIC -> new SpeciesBehaviorProfile.Shelter(enabled, 12, 0.85, 0.70, 0.95, 40, 160, 1.00);
            case OMNIVORE -> new SpeciesBehaviorProfile.Shelter(enabled, 18, 0.50, 0.35, 0.68, 80, 260, 1.05);
            case HERBIVORE -> new SpeciesBehaviorProfile.Shelter(enabled, 20, 0.50, 0.34, 0.70, 100, 290, 1.03);
            case GENERIC -> new SpeciesBehaviorProfile.Shelter(enabled, 18, 0.55, 0.35, 0.70, 80, 240, 1.05);
        };
    }

    private static SpeciesBehaviorProfile.Herd herd(
            Archetype archetype,
            boolean enabled,
            Set<AnimalBehaviorTag> tags
    ) {
        if (tags.contains(AnimalBehaviorTag.FLOCK) || archetype == Archetype.BIRD) {
            return new SpeciesBehaviorProfile.Herd(enabled, 16, 7.0, 0.50, 0.95);
        }
        if (tags.contains(AnimalBehaviorTag.PACK) || archetype == Archetype.WOLF) {
            return new SpeciesBehaviorProfile.Herd(enabled, 24, 10.0, 0.65, 1.00);
        }
        if (archetype == Archetype.OMNIVORE) {
            return new SpeciesBehaviorProfile.Herd(enabled, 18, 8.0, 0.55, 0.90);
        }
        if (archetype == Archetype.AQUATIC) {
            return new SpeciesBehaviorProfile.Herd(enabled, 18, 6.0, 0.48, 1.00);
        }
        return new SpeciesBehaviorProfile.Herd(enabled, 20, 8.0, 0.50, 0.90);
    }

    private static SpeciesBehaviorProfile.Prey prey(Archetype archetype, boolean enabled) {
        return switch (archetype) {
            case BIRD -> new SpeciesBehaviorProfile.Prey(
                    enabled, 20, 320, 14, 1.50, List.of(EcosystemTags.PREDATORS_ID));
            case HERBIVORE -> new SpeciesBehaviorProfile.Prey(
                    enabled, 20, 280, 14, 1.38, List.of(EcosystemTags.PREDATORS_ID));
            case OMNIVORE -> new SpeciesBehaviorProfile.Prey(
                    enabled, 18, 260, 12, 1.35, List.of(EcosystemTags.PREDATORS_ID));
            case AQUATIC -> new SpeciesBehaviorProfile.Prey(
                    enabled, 16, 220, 10, 1.25, List.of(EcosystemTags.PREDATORS_ID));
            case WOLF, GENERIC -> new SpeciesBehaviorProfile.Prey(
                    enabled, 16, 240, 12, 1.35, List.of(EcosystemTags.PREDATORS_ID));
        };
    }

    private static SpeciesBehaviorProfile.Predator predator(
            Archetype archetype,
            boolean enabled,
            boolean wolf
    ) {
        if (archetype == Archetype.WOLF) {
            return new SpeciesBehaviorProfile.Predator(
                    enabled, 24, 0.80, 12_000, 4, 24, 1.18, true,
                    List.of(EcosystemTags.WOLF_PREY_ID));
        }
        return new SpeciesBehaviorProfile.Predator(
                enabled, 20, 0.78, 12_000, 4, 20, 1.15, true,
                wolf ? List.of(EcosystemTags.WOLF_PREY_ID) : List.of(EcosystemTags.PREY_ID));
    }

    private enum Archetype {
        GENERIC,
        HERBIVORE,
        OMNIVORE,
        BIRD,
        WOLF,
        AQUATIC
    }
}
