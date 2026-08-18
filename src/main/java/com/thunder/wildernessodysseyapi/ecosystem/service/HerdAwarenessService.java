package com.thunder.wildernessodysseyapi.ecosystem.service;

import com.thunder.wildernessodysseyapi.ecosystem.api.EcosystemBehaviorState;
import com.thunder.wildernessodysseyapi.ecosystem.api.EnvironmentalContext;
import com.thunder.wildernessodysseyapi.ecosystem.api.WildlifeWeatherResponse;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.PathfinderMob;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.WeakHashMap;

/**
 * Resolves deterministic local group leaders and shares their broad decisions.
 *
 * <p>One short-lived nearby-entity query populates roles for every member of a
 * local same-species group. Followers can then inherit the leader's cached
 * decision without repeating weather, water, shelter, food, or threat work.</p>
 */
public final class HerdAwarenessService {

    private static final long ROLE_CACHE_TICKS = 40L;
    private final NearbyLivingEntityCache nearbyEntities;
    private final Map<ServerLevel, Map<UUID, CachedRole>> roles = new WeakHashMap<>();
    private final Map<ServerLevel, Map<UUID, GroupDecision>> decisions = new WeakHashMap<>();

    HerdAwarenessService(NearbyLivingEntityCache nearbyEntities) {
        this.nearbyEntities = nearbyEntities;
    }

    /** Returns bounded same-species candidates for the persistent group manager. */
    public List<PathfinderMob> candidates(PathfinderMob animal, int radius) {
        if (!(animal.level() instanceof ServerLevel level)) {
            return List.of(animal);
        }
        List<PathfinderMob> candidates = new ArrayList<>();
        for (LivingEntity candidate : nearbyEntities.query(
                level, animal.blockPosition(), radius, level.getGameTime())) {
            if (candidate instanceof PathfinderMob member && member.getType() == animal.getType()) {
                candidates.add(member);
            }
        }
        if (candidates.stream().noneMatch(candidate -> candidate == animal)) {
            candidates.add(animal);
        }
        return List.copyOf(candidates);
    }

    /** Returns the stable local leader view used by forecast-sharing compatibility code. */
    public GroupMembership group(PathfinderMob animal, int radius) {
        List<PathfinderMob> candidates = new ArrayList<>(candidates(animal, radius));
        candidates.sort(Comparator.comparing(PathfinderMob::getUUID));
        return new GroupMembership(candidates.getFirst(), candidates.size());
    }

    /** Returns a cached deterministic role for one local same-species group. */
    public GroupRole role(PathfinderMob animal, int radius) {
        if (!(animal.level() instanceof ServerLevel level)) {
            return GroupRole.individual(animal);
        }
        long gameTime = level.getGameTime();
        Map<UUID, CachedRole> levelRoles = roles.computeIfAbsent(level, ignored -> new HashMap<>());
        CachedRole cached = levelRoles.get(animal.getUUID());
        if (cached != null && cached.expiresAt() > gameTime) {
            return cached.role();
        }

        // The shared nearby cache bounds the only entity scan needed to assign this group.
        List<PathfinderMob> members = new ArrayList<>();
        for (LivingEntity candidate : nearbyEntities.query(level, animal.blockPosition(), radius, gameTime)) {
            if (candidate instanceof PathfinderMob member && member.getType() == animal.getType()) {
                members.add(member);
            }
        }
        if (members.stream().noneMatch(member -> member == animal)) {
            members.add(animal);
        }
        members.sort(Comparator.comparing(PathfinderMob::getUUID));
        UUID leaderId = members.getFirst().getUUID();
        double x = 0.0;
        double y = 0.0;
        double z = 0.0;
        for (PathfinderMob member : members) {
            x += member.getX();
            y += member.getY();
            z += member.getZ();
        }
        BlockPos center = BlockPos.containing(x / members.size(), y / members.size(), z / members.size());
        long expiresAt = gameTime + ROLE_CACHE_TICKS;
        for (PathfinderMob member : members) {
            GroupRole role = new GroupRole(
                    leaderId,
                    member.getUUID().equals(leaderId),
                    members.size(),
                    center,
                    member.distanceToSqr(center.getCenter())
            );
            levelRoles.put(member.getUUID(), new CachedRole(expiresAt, role));
        }
        return levelRoles.get(animal.getUUID()).role();
    }

    /** Returns group context for the immutable environmental snapshot. */
    public Optional<EnvironmentalContext.HerdCenter> assess(PathfinderMob animal, int radius) {
        GroupRole role = role(animal, radius);
        if (role.members() <= 1) {
            return Optional.empty();
        }
        return Optional.of(new EnvironmentalContext.HerdCenter(
                role.center(), role.members(), role.distanceSquared(), role.leaderId(), role.leader()));
    }

    /** Publishes the expensive leader decision for followers until the next major decision. */
    public void publish(ServerLevel level, GroupRole role, GroupDecision decision) {
        if (role.leader()) {
            decisions.computeIfAbsent(level, ignored -> new HashMap<>()).put(role.leaderId(), decision);
        }
    }

    /** Returns a still-current leader decision for a follower. */
    public Optional<GroupDecision> inherited(ServerLevel level, GroupRole role, long gameTime) {
        if (role.leader()) {
            return Optional.empty();
        }
        GroupDecision decision = decisions.getOrDefault(level, Map.of()).get(role.leaderId());
        return decision == null || decision.expiresAt() <= gameTime
                ? Optional.empty()
                : Optional.of(decision);
    }

    /** Releases transient group roles and destinations when a level unloads. */
    public void clear(ServerLevel level) {
        roles.remove(level);
        decisions.remove(level);
    }

    /** Deterministic role and centroid for one local same-species group. */
    public record GroupRole(UUID leaderId, boolean leader, int members, BlockPos center, double distanceSquared) {
        private static GroupRole individual(PathfinderMob animal) {
            return new GroupRole(animal.getUUID(), true, 1, animal.blockPosition(), 0.0);
        }

        /** Compact diagnostic describing whether this animal owns the decision. */
        public String diagnostic() {
            return (leader ? "leader" : "follower")
                    + " members=" + members
                    + " leader=" + leaderId.toString().substring(0, 8);
        }
    }

    /** Compatibility view for services that need the elected entity rather than only its UUID. */
    public record GroupMembership(PathfinderMob leader, int members) {
        public GroupMembership {
            if (leader == null) {
                throw new IllegalArgumentException("Group leader cannot be null");
            }
            members = Math.max(1, members);
        }

        public boolean isLeader(PathfinderMob animal) {
            return animal != null && animal.getUUID().equals(leader.getUUID());
        }
    }

    /** Broad state and shared destination selected by a group leader. */
    public record GroupDecision(
            EcosystemBehaviorState state,
            BlockPos destination,
            String reason,
            WildlifeWeatherResponse weatherResponse,
            long expiresAt
    ) {
        public GroupDecision {
            destination = destination == null ? null : destination.immutable();
        }
    }

    private record CachedRole(long expiresAt, GroupRole role) {
    }
}
