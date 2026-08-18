package com.thunder.wildernessodysseyapi.ecosystem.behavior;

import com.thunder.wildernessodysseyapi.ecosystem.api.SpeciesBehaviorProfile;
import com.thunder.wildernessodysseyapi.ecosystem.config.EcosystemConfig;
import com.thunder.wildernessodysseyapi.ecosystem.data.SpeciesBehaviorProfileManager;
import com.thunder.wildernessodysseyapi.ecosystem.group.AnimalGroup;
import com.thunder.wildernessodysseyapi.ecosystem.group.GroupBehavior;
import com.thunder.wildernessodysseyapi.ecosystem.group.GroupFormationPlanner;
import com.thunder.wildernessodysseyapi.ecosystem.group.GroupMemberPlan;
import com.thunder.wildernessodysseyapi.ecosystem.group.GroupRole;
import com.thunder.wildernessodysseyapi.ecosystem.service.EcosystemServices;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.phys.Vec3;

import java.util.EnumSet;
import java.util.Optional;

/**
 * Cheap relative movement for a cached group follower.
 *
 * <p>Nearby followers steer through {@code MoveControl} and do not calculate a
 * path. A follower asks its normal navigation for a path only when it falls far
 * behind or makes no direct progress, then waits on a long repath cooldown.
 * Stable loose offsets, reaction delay, and short independent pauses keep the
 * group natural while vanilla and modded idle/grazing goals remain available.</p>
 */
public final class GroupFollowerGoal extends Goal {

    private static final int DIRECT_MOVEMENT_UPDATE_TICKS = 6;
    private static final int NO_PROGRESS_PATH_THRESHOLD_TICKS = 30;
    private final PathfinderMob animal;
    private AnimalGroup group;
    private PathfinderMob leader;
    private SpeciesBehaviorProfile profile;
    private GroupMemberPlan memberPlan;
    private long observedRevision = Long.MIN_VALUE;
    private long reactAt;
    private long nextDirectMovementAt;
    private long nextPathAt;
    private long lastProgressAt;
    private double closestTargetDistanceSquared = Double.MAX_VALUE;
    private boolean usingNavigation;

    /** Creates the low-priority follower controller for one profiled pathfinding animal. */
    public GroupFollowerGoal(PathfinderMob animal) {
        this.animal = animal;
        setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        if (!(animal.level() instanceof ServerLevel level)
                || !animal.isAlive()
                || !EcosystemConfig.ENABLED.get()
                || !EcosystemConfig.HERD_BEHAVIOR_ENABLED.get()
                || !EcosystemConfig.GROUP_AI_ENABLED.get()) {
            return false;
        }
        Optional<AnimalGroup> resolvedGroup = EcosystemServices.groups().groupFor(animal);
        if (resolvedGroup.isEmpty()) {
            return false;
        }
        AnimalGroup candidateGroup = resolvedGroup.get();
        if (candidateGroup.roleOf(animal.getUUID()).orElse(null) != GroupRole.FOLLOWER) {
            return false;
        }
        Optional<PathfinderMob> resolvedLeader = EcosystemServices.groups().resolveLeader(candidateGroup);
        Optional<GroupMemberPlan> resolvedPlan = candidateGroup.planFor(animal.getUUID());
        Optional<SpeciesBehaviorProfile> resolvedProfile = SpeciesBehaviorProfileManager.profileFor(animal);
        if (resolvedLeader.isEmpty() || resolvedPlan.isEmpty()
                || resolvedProfile.isEmpty() || !resolvedProfile.get().herd().enabled()) {
            return false;
        }

        long gameTime = level.getGameTime();
        if (candidateGroup.revision() != observedRevision) {
            observedRevision = candidateGroup.revision();
            reactAt = gameTime + resolvedPlan.get().reactionDelayTicks();
        }
        if (gameTime < reactAt) {
            return false;
        }

        Vec3 target = formationTarget(candidateGroup, resolvedLeader.get(), resolvedPlan.get(), gameTime);
        double targetDistanceSquared = animal.position().distanceToSqr(target);
        double followDistance = resolvedPlan.get().followDistance(EcosystemConfig.FOLLOW_DISTANCE.get());
        double leaderDistanceSquared = animal.distanceToSqr(resolvedLeader.get());
        boolean activeDecision = candidateGroup.state() != GroupBehavior.IDLE;
        if (!activeDecision && leaderDistanceSquared <= followDistance * followDistance) {
            return false;
        }
        if (activeDecision
                && candidateGroup.state() != GroupBehavior.ALERT
                && targetDistanceSquared <= 4.0) {
            return false;
        }
        double catchUpDistance = followDistance + EcosystemConfig.GROUP_FORMATION_RADIUS.get() * 1.5;
        if (candidateGroup.state() != GroupBehavior.FLEE
                && candidateGroup.state() != GroupBehavior.ALERT
                && resolvedPlan.get().pausesAt(gameTime)
                && leaderDistanceSquared <= catchUpDistance * catchUpDistance) {
            return false;
        }

        group = candidateGroup;
        leader = resolvedLeader.get();
        memberPlan = resolvedPlan.get();
        profile = resolvedProfile.get();
        return true;
    }

    @Override
    public boolean canContinueToUse() {
        if (group == null || leader == null || memberPlan == null || profile == null
                || !animal.isAlive() || !leader.isAlive()
                || group.roleOf(animal.getUUID()).orElse(null) != GroupRole.FOLLOWER) {
            return false;
        }
        long gameTime = animal.level().getGameTime();
        if (group.revision() != observedRevision) {
            return false;
        }
        if (group.state() == GroupBehavior.ALERT) {
            return true;
        }
        double followDistance = memberPlan.followDistance(EcosystemConfig.FOLLOW_DISTANCE.get());
        double catchUpDistance = followDistance + EcosystemConfig.GROUP_FORMATION_RADIUS.get() * 1.5;
        if (group.state() != GroupBehavior.FLEE
                && memberPlan.pausesAt(gameTime)
                && animal.distanceToSqr(leader) <= catchUpDistance * catchUpDistance) {
            return false;
        }
        Vec3 target = formationTarget(group, leader, memberPlan, gameTime);
        if (group.state() == GroupBehavior.IDLE) {
            return animal.distanceToSqr(leader) > followDistance * followDistance * 0.64;
        }
        return animal.position().distanceToSqr(target) > 2.25;
    }

    @Override
    public void start() {
        long gameTime = animal.level().getGameTime();
        nextDirectMovementAt = gameTime;
        nextPathAt = gameTime;
        lastProgressAt = gameTime;
        closestTargetDistanceSquared = Double.MAX_VALUE;
        usingNavigation = false;
        animal.getNavigation().stop();
    }

    @Override
    public void tick() {
        if (group == null || leader == null || memberPlan == null || profile == null) {
            return;
        }
        long gameTime = animal.level().getGameTime();
        if (group.state() == GroupBehavior.ALERT) {
            animal.getNavigation().stop();
            animal.getLookControl().setLookAt(leader, 28.0F, 24.0F);
            return;
        }
        Vec3 target = formationTarget(group, leader, memberPlan, gameTime);
        double targetDistanceSquared = animal.position().distanceToSqr(target);
        if (targetDistanceSquared + 0.5 < closestTargetDistanceSquared) {
            closestTargetDistanceSquared = targetDistanceSquared;
            lastProgressAt = gameTime;
        }

        animal.getLookControl().setLookAt(leader, 25.0F, 25.0F);
        double followDistance = memberPlan.followDistance(EcosystemConfig.FOLLOW_DISTANCE.get());
        double catchUpDistance = followDistance + EcosystemConfig.GROUP_FORMATION_RADIUS.get() * 1.5;
        boolean farBehind = animal.distanceToSqr(leader) > catchUpDistance * catchUpDistance;
        boolean obstructed = gameTime - lastProgressAt >= NO_PROGRESS_PATH_THRESHOLD_TICKS
                && targetDistanceSquared > 9.0;
        double speed = movementSpeed(group.state(), profile, farBehind);

        // Individual pathfinding is a fallback, not the normal follower movement mode.
        if ((farBehind || obstructed) && gameTime >= nextPathAt) {
            animal.getNavigation().moveTo(target.x, target.y, target.z, speed);
            usingNavigation = true;
            long pathCooldown = Math.max(40L, EcosystemConfig.LEADER_DECISION_INTERVAL.get());
            nextPathAt = gameTime + pathCooldown + Math.floorMod(animal.getId(), 17);
            lastProgressAt = gameTime;
            closestTargetDistanceSquared = targetDistanceSquared;
            return;
        }
        if (usingNavigation && !farBehind && !obstructed) {
            animal.getNavigation().stop();
            usingNavigation = false;
        }
        if (!usingNavigation && gameTime >= nextDirectMovementAt) {
            animal.getMoveControl().setWantedPosition(target.x, target.y, target.z, speed);
            nextDirectMovementAt = gameTime + DIRECT_MOVEMENT_UPDATE_TICKS
                    + Math.floorMod(animal.getId(), 4);
        }
    }

    @Override
    public void stop() {
        if (usingNavigation) {
            animal.getNavigation().stop();
        }
        usingNavigation = false;
        group = null;
        leader = null;
        profile = null;
        memberPlan = null;
    }

    @Override
    public boolean requiresUpdateEveryTick() {
        return true;
    }

    private static Vec3 formationTarget(
            AnimalGroup group,
            PathfinderMob leader,
            GroupMemberPlan plan,
            long gameTime
    ) {
        Vec3 destination = group.destination() == null ? null : group.destination().getCenter();
        return GroupFormationPlanner.target(
                leader.position(),
                destination,
                leader.getYRot(),
                plan,
                EcosystemConfig.GROUP_FORMATION_RADIUS.get(),
                gameTime
        );
    }

    private static double movementSpeed(
            GroupBehavior behavior,
            SpeciesBehaviorProfile profile,
            boolean catchingUp
    ) {
        double speed = behavior == GroupBehavior.FLEE && profile.prey().enabled()
                ? profile.prey().fleeSpeed()
                : profile.herd().moveSpeed();
        return catchingUp ? Math.min(2.5, speed * 1.15) : speed;
    }
}
