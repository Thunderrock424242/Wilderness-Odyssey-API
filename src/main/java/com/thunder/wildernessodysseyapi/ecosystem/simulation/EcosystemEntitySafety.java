package com.thunder.wildernessodysseyapi.ecosystem.simulation;

import com.thunder.wildernessodysseyapi.core.ModAttachments;
import com.thunder.wildernessodysseyapi.ecosystem.EcosystemTags;
import com.thunder.wildernessodysseyapi.ecosystem.state.AnimalNeedsState;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.AgeableMob;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.animal.FlyingAnimal;
import net.minecraft.world.entity.animal.WaterAnimal;
import net.minecraft.world.entity.animal.horse.AbstractHorse;

/**
 * Conservative veto policy for converting real wildlife into abstract population.
 *
 * <p>Vanilla/NeoForge persistence flags, a data-pack opt-out tag, and active
 * relationships all win over performance optimization. This is intentionally
 * stricter than vanilla despawning.</p>
 */
public final class EcosystemEntitySafety {

    private static final int COMBAT_PROTECTION_TICKS = 400;

    private EcosystemEntitySafety() {
    }

    /** Returns whether this live entity can safely lose individual identity. */
    public static boolean mayAbstract(PathfinderMob animal) {
        AnimalNeedsState needs = animal.getData(ModAttachments.ANIMAL_NEEDS);
        boolean recentCombat = animal.hurtTime > 0
                || recent(animal.tickCount, animal.getLastHurtByMobTimestamp())
                || recent(animal.tickCount, animal.getLastHurtMobTimestamp());
        boolean externalNoAi = animal.isNoAi() && !needs.simulationAiSuspended();
        return mayAbstract(new ProtectionFacts(
                animal.hasCustomName(),
                animal instanceof TamableAnimal tamable && tamable.isTame()
                        || animal instanceof AbstractHorse horse && horse.isTamed(),
                animal.isPersistenceRequired(),
                animal.requiresCustomPersistence(),
                animal.getType().builtInRegistryHolder().is(EcosystemTags.NEVER_ABSTRACT),
                animal.isPassenger(),
                animal.isVehicle(),
                animal.isLeashed(),
                animal.getTarget() != null || recentCombat,
                externalNoAi,
                !(animal instanceof Animal || animal instanceof WaterAnimal || animal instanceof FlyingAnimal),
                !animal.isAlive() || animal.isRemoved(),
                !animal.getTags().isEmpty(),
                animal.getHealth() + 0.01F < animal.getMaxHealth(),
                animal instanceof AgeableMob ageable && ageable.isBaby(),
                animal instanceof Animal breedingAnimal && breedingAnimal.isInLove()
        ));
    }

    /** Pure predicate used by tests and integrations that precompute protection facts. */
    public static boolean mayAbstract(ProtectionFacts facts) {
        return !facts.named()
                && !facts.tamed()
                && !facts.persistenceRequired()
                && !facts.customPersistence()
                && !facts.taggedNeverAbstract()
                && !facts.passenger()
                && !facts.vehicle()
                && !facts.leashed()
                && !facts.interactingOrInCombat()
                && !facts.externallyNoAi()
                && !facts.notWildlife()
                && !facts.notLive()
                && !facts.taggedEntity()
                && !facts.injured()
                && !facts.juvenile()
                && !facts.breeding();
    }

    static boolean recent(int currentTick, int eventTick) {
        return eventTick > 0 && currentTick - eventTick <= COMBAT_PROTECTION_TICKS;
    }

    /** Facts that force a real entity to remain individually represented. */
    public record ProtectionFacts(
            boolean named,
            boolean tamed,
            boolean persistenceRequired,
            boolean customPersistence,
            boolean taggedNeverAbstract,
            boolean passenger,
            boolean vehicle,
            boolean leashed,
            boolean interactingOrInCombat,
            boolean externallyNoAi,
            boolean notWildlife,
            boolean notLive,
            boolean taggedEntity,
            boolean injured,
            boolean juvenile,
            boolean breeding
    ) {
        /** Retains the original protection-facts API for integrations. */
        public ProtectionFacts(
                boolean named, boolean tamed, boolean persistenceRequired, boolean customPersistence,
                boolean taggedNeverAbstract, boolean passenger, boolean vehicle, boolean leashed,
                boolean interactingOrInCombat, boolean externallyNoAi
        ) {
            this(named, tamed, persistenceRequired, customPersistence, taggedNeverAbstract,
                    passenger, vehicle, leashed, interactingOrInCombat, externallyNoAi,
                    false, false, false, false, false, false);
        }
    }
}
