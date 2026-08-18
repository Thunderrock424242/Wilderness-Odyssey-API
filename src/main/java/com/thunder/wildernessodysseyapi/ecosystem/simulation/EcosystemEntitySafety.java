package com.thunder.wildernessodysseyapi.ecosystem.simulation;

import com.thunder.wildernessodysseyapi.core.ModAttachments;
import com.thunder.wildernessodysseyapi.ecosystem.EcosystemTags;
import com.thunder.wildernessodysseyapi.ecosystem.state.AnimalNeedsState;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.TamableAnimal;

/**
 * Conservative veto policy for converting real wildlife into abstract population.
 *
 * <p>Vanilla/NeoForge persistence flags, a data-pack opt-out tag, and active
 * relationships all win over performance optimization. This is intentionally
 * stricter than vanilla despawning.</p>
 */
public final class EcosystemEntitySafety {

    private static final int COMBAT_PROTECTION_TICKS = 100;

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
                animal instanceof TamableAnimal tamable && tamable.isTame(),
                animal.isPersistenceRequired(),
                animal.requiresCustomPersistence(),
                animal.getType().builtInRegistryHolder().is(EcosystemTags.NEVER_ABSTRACT),
                animal.isPassenger(),
                animal.isVehicle(),
                animal.isLeashed(),
                animal.getTarget() != null || recentCombat,
                externalNoAi
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
                && !facts.externallyNoAi();
    }

    private static boolean recent(int currentTick, int eventTick) {
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
            boolean externallyNoAi
    ) {
    }
}
