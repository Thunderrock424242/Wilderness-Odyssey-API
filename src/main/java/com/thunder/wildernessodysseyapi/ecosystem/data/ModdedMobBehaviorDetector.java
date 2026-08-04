package com.thunder.wildernessodysseyapi.ecosystem.data;

import com.thunder.wildernessodysseyapi.ecosystem.api.AnimalBehaviorTag;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.animal.AbstractSchoolingFish;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.animal.Chicken;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.entity.animal.FlyingAnimal;
import net.minecraft.world.entity.animal.Pig;
import net.minecraft.world.entity.animal.Rabbit;
import net.minecraft.world.entity.animal.Sheep;
import net.minecraft.world.entity.animal.WaterAnimal;
import net.minecraft.world.entity.animal.Wolf;
import net.minecraft.world.entity.animal.horse.AbstractHorse;
import net.minecraft.world.entity.monster.Enemy;

import java.util.EnumSet;
import java.util.Optional;
import java.util.Set;

/**
 * Conservatively infers ecosystem labels from stable Minecraft type and AI contracts.
 *
 * <p>This fallback only considers non-Minecraft registry entries that expose an
 * animal, flight, or aquatic contract. Hostile mobs and unrelated pathfinding
 * entities are deliberately ignored. Config assignments and JSON profiles are
 * resolved before this detector, so explicit modpack knowledge always wins.</p>
 */
public final class ModdedMobBehaviorDetector {

    private ModdedMobBehaviorDetector() {
    }

    /**
     * Detects a conservative behavior set for a compatible third-party mob.
     *
     * @param mob runtime mob whose registered type and existing goals are inspected
     * @return inferred behavior labels, or empty when automatic integration is unsafe
     */
    public static Optional<Set<AnimalBehaviorTag>> detect(PathfinderMob mob) {
        ResourceLocation entityId = BuiltInRegistries.ENTITY_TYPE.getKey(mob.getType());
        if (entityId == null || "minecraft".equals(entityId.getNamespace()) || mob instanceof Enemy) {
            return Optional.empty();
        }

        boolean animal = mob instanceof Animal;
        boolean flying = mob instanceof FlyingAnimal;
        boolean aquatic = mob instanceof WaterAnimal;
        if (!animal && !flying && !aquatic) {
            return Optional.empty();
        }

        boolean predatoryTargeting = mob.targetSelector.getAvailableGoals().stream()
                .anyMatch(wrapped -> wrapped.getGoal() instanceof NearestAttackableTargetGoal<?>);
        return infer(new Traits(
                inheritedFamily(mob),
                animal,
                flying,
                aquatic,
                mob instanceof AbstractSchoolingFish,
                predatoryTargeting
        ));
    }

    // Keeping the inference pure makes the compatibility policy testable without
    // constructing a live Minecraft entity or relying on a specific third-party mod.
    static Optional<Set<AnimalBehaviorTag>> infer(Traits traits) {
        if (!traits.animal() && !traits.flying() && !traits.aquatic()) {
            return Optional.empty();
        }

        EnumSet<AnimalBehaviorTag> tags = EnumSet.noneOf(AnimalBehaviorTag.class);
        if (traits.inheritedFamily() != KnownFamily.NONE) {
            tags.add(traits.inheritedFamily().behaviorTag());
        } else if (traits.aquatic()) {
            tags.add(AnimalBehaviorTag.AQUATIC);
            if (traits.schooling()) {
                tags.add(AnimalBehaviorTag.FLOCK);
            }
        } else if (traits.flying()) {
            tags.add(AnimalBehaviorTag.BIRD);
        } else {
            tags.add(AnimalBehaviorTag.ANIMAL);
        }

        if (traits.predatoryTargeting() && traits.inheritedFamily() != KnownFamily.WOLF) {
            tags.add(AnimalBehaviorTag.PREDATOR);
            tags.add(AnimalBehaviorTag.SOLITARY);
        }
        return Optional.of(Set.copyOf(tags));
    }

    private static KnownFamily inheritedFamily(PathfinderMob mob) {
        if (mob instanceof Wolf) {
            return KnownFamily.WOLF;
        }
        if (mob instanceof Chicken) {
            return KnownFamily.BIRD;
        }
        if (mob instanceof Pig) {
            return KnownFamily.OMNIVORE;
        }
        if (mob instanceof Cow || mob instanceof Sheep || mob instanceof Rabbit || mob instanceof AbstractHorse) {
            return KnownFamily.HERBIVORE;
        }
        return KnownFamily.NONE;
    }

    enum KnownFamily {
        NONE(null),
        HERBIVORE(AnimalBehaviorTag.HERBIVORE),
        OMNIVORE(AnimalBehaviorTag.OMNIVORE),
        BIRD(AnimalBehaviorTag.BIRD),
        WOLF(AnimalBehaviorTag.WOLF);

        private final AnimalBehaviorTag behaviorTag;

        KnownFamily(AnimalBehaviorTag behaviorTag) {
            this.behaviorTag = behaviorTag;
        }

        AnimalBehaviorTag behaviorTag() {
            return behaviorTag;
        }
    }

    /** Stable runtime facts used by the conservative inference policy. */
    record Traits(
            KnownFamily inheritedFamily,
            boolean animal,
            boolean flying,
            boolean aquatic,
            boolean schooling,
            boolean predatoryTargeting
    ) {
    }
}
