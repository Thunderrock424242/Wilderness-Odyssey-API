package com.thunder.wildernessodysseyapi.ecosystem.behavior;

import com.thunder.wildernessodysseyapi.core.ModAttachments;
import com.thunder.wildernessodysseyapi.ecosystem.api.EcosystemBehaviorState;
import com.thunder.wildernessodysseyapi.ecosystem.api.EnvironmentalContext;
import com.thunder.wildernessodysseyapi.ecosystem.api.SpeciesBehaviorProfile;
import com.thunder.wildernessodysseyapi.ecosystem.group.AnimalGroup;
import com.thunder.wildernessodysseyapi.ecosystem.group.GroupBehavior;
import com.thunder.wildernessodysseyapi.ecosystem.service.EcosystemServices;
import com.thunder.wildernessodysseyapi.ecosystem.state.AnimalNeedsState;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.PathfinderMob;

import java.util.Objects;
import java.util.UUID;

/** Completes cheap individual actions against a leader's shared decision and destination. */
final class GroupMemberInteractions {
    private final PathfinderMob animal;
    private final AnimalNeedsState needs;
    private UUID groupId;
    private long revision = Long.MIN_VALUE;
    private long startedAt = Long.MIN_VALUE;
    private boolean completed;
    private GroupBehavior action = GroupBehavior.IDLE;
    private BlockPos destination;
    private boolean finishingLeaderInteraction;
    private long completedLeaderRevision = Long.MIN_VALUE;
    private EnvironmentalContext.WaterTarget waterTarget;
    private long nextWaterCheckAt;

    GroupMemberInteractions(PathfinderMob animal) {
        this.animal = animal;
        needs = animal.getData(ModAttachments.ANIMAL_NEEDS);
    }

    /** Returns whether the member should hold still to finish the current local interaction. */
    boolean update(AnimalGroup group, PathfinderMob leader, SpeciesBehaviorProfile profile, long gameTime) {
        if (!(animal.level() instanceof ServerLevel level)) {
            return false;
        }
        boolean changedGroup = !group.id().equals(groupId);
        if (changedGroup || revision != group.revision()) {
            boolean sameAction = !changedGroup && action == group.state()
                    && Objects.equals(destination, group.destination());
            boolean finishingStartedAction = !changedGroup && !completed && startedAt != Long.MIN_VALUE
                    && group.state() == GroupBehavior.IDLE
                    && group.completedInteractionRevision()
                    == (finishingLeaderInteraction ? completedLeaderRevision : revision);
            if (!sameAction && !finishingStartedAction) {
                interrupt();
                startedAt = Long.MIN_VALUE;
                action = group.state();
                destination = group.destination();
                completed = false;
                waterTarget = group.waterTarget();
                nextWaterCheckAt = 0L;
                finishingLeaderInteraction = false;
                completedLeaderRevision = Long.MIN_VALUE;
            } else if (finishingStartedAction) {
                // Normal leader completion releases only its own action. A
                // member already at the destination keeps its individual clock.
                finishingLeaderInteraction = true;
                completedLeaderRevision = group.completedInteractionRevision();
            }
            groupId = group.id();
            revision = group.revision();
        }
        if (completed) {
            return false;
        }
        if (action == GroupBehavior.SEEK_WATER && profile.drinking().enabled()) {
            BlockPos approach = destination;
            if (approach == null || animal.distanceToSqr(approach.getCenter()) > 2.25) {
                interrupt();
                return false;
            }
            if (waterTarget == null && gameTime < nextWaterCheckAt) {
                return false;
            }
            if (gameTime >= nextWaterCheckAt) {
                // A state-only integration may supply the dry approach without
                // a water observation. Inspect its four immediate neighbors only.
                if (waterTarget == null) {
                    waterTarget = adjacentWater(approach, profile);
                }
                if (waterTarget == null || !EcosystemServices.isSafeWaterTarget(animal, profile, waterTarget)) {
                    interrupt();
                    waterTarget = null;
                    nextWaterCheckAt = gameTime + 10L;
                    return false;
                }
                nextWaterCheckAt = gameTime + 10L;
            }
            begin(EcosystemBehaviorState.DRINK, approach, gameTime);
            needs.setWaterPosition(waterTarget.waterPosition());
            if (gameTime - startedAt >= profile.drinking().durationTicks()) {
                needs.restoreThirst(profile.drinking().thirstRestored());
                complete();
                return false;
            }
            return true;
        }
        if (action == GroupBehavior.REST) {
            if (animal.distanceToSqr(leader) > profile.herd().preferredDistance() * profile.herd().preferredDistance()) {
                interrupt();
                return false;
            }
            begin(EcosystemBehaviorState.REST, null, gameTime);
            if (gameTime - startedAt >= profile.environment().restDurationTicks()) {
                needs.recoverRest(0.20);
                complete();
                return false;
            }
            return true;
        }
        if (action == GroupBehavior.FEED && !profile.predator().enabled()
                && group.foodAvailability() >= profile.environment().minimumFoodForForage()
                && destination != null
                && animal.distanceToSqr(destination.getCenter()) <= 4.0) {
            needs.restoreHunger(0.12);
            complete();
        }
        return false;
    }

    private EnvironmentalContext.WaterTarget adjacentWater(BlockPos approach, SpeciesBehaviorProfile profile) {
        for (Direction direction : Direction.Plane.HORIZONTAL) {
            EnvironmentalContext.WaterTarget candidate = new EnvironmentalContext.WaterTarget(
                    approach.relative(direction), approach, 0.0);
            if (EcosystemServices.isSafeWaterTarget(animal, profile, candidate)) {
                return candidate;
            }
        }
        EnvironmentalContext.WaterTarget swimming = new EnvironmentalContext.WaterTarget(approach, approach, 0.0);
        return EcosystemServices.isSafeWaterTarget(animal, profile, swimming) ? swimming : null;
    }

    private void begin(EcosystemBehaviorState behavior, BlockPos target, long gameTime) {
        if (startedAt == Long.MIN_VALUE) {
            startedAt = gameTime;
            needs.begin(behavior, target, gameTime);
            needs.markInteractionStarted(gameTime);
        }
    }

    private void complete() {
        completed = true;
        needs.idle();
    }

    /** Interruption cannot count travel, panic, or another goal as drinking/rest time. */
    void interrupt() {
        if (!completed) {
            if (startedAt != Long.MIN_VALUE
                    && (action == GroupBehavior.SEEK_WATER && needs.behavior() == EcosystemBehaviorState.DRINK
                    || action == GroupBehavior.REST && needs.behavior() == EcosystemBehaviorState.REST)) {
                needs.idle();
            }
            startedAt = Long.MIN_VALUE;
        }
        if (finishingLeaderInteraction) {
            // A higher-priority goal or lost proximity ends this final local
            // continuation; an idle group cannot start it again afterward.
            action = GroupBehavior.IDLE;
            destination = null;
            finishingLeaderInteraction = false;
            completedLeaderRevision = Long.MIN_VALUE;
        }
    }
}
