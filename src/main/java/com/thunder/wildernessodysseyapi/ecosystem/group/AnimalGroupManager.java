package com.thunder.wildernessodysseyapi.ecosystem.group;

import com.thunder.wildernessodysseyapi.core.ModAttachments;
import com.thunder.wildernessodysseyapi.ecosystem.api.EnvironmentalContext;
import com.thunder.wildernessodysseyapi.ecosystem.api.SpeciesBehaviorProfile;
import com.thunder.wildernessodysseyapi.ecosystem.data.SpeciesBehaviorProfileManager;
import com.thunder.wildernessodysseyapi.ecosystem.state.AnimalNeedsState;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.AgeableMob;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Owns transient, per-level animal groups without a persistent tick loop.
 *
 * <p>New groups come from a bounded local candidate lookup supplied by the
 * ecosystem cache. Existing membership is resolved directly by UUID, and only
 * a leader performs cooldown-gated validation and recruitment. Entity unload
 * events remove members immediately and elect from the remaining loaded cache.</p>
 */
public final class AnimalGroupManager {

    private final CandidateLocator candidateLocator;
    private final Map<ServerLevel, LevelGroups> levels = new IdentityHashMap<>();

    /** Creates a manager backed by the ecosystem's bounded local discovery service. */
    public AnimalGroupManager(CandidateLocator candidateLocator) {
        this.candidateLocator = candidateLocator;
    }

    /** Returns a cached group without performing discovery or validation. */
    public Optional<AnimalGroup> groupFor(PathfinderMob animal) {
        if (!(animal.level() instanceof ServerLevel level)) {
            return Optional.empty();
        }
        LevelGroups state = levels.get(level);
        if (state == null) {
            return Optional.empty();
        }
        AnimalGroup group = state.membership().get(animal.getUUID());
        return group != null && group.contains(animal.getUUID()) ? Optional.of(group) : Optional.empty();
    }

    /**
     * Joins a nearby compatible group or forms one from locally discovered animals.
     *
     * <p>The caller must charge this operation to the ecosystem expensive-work
     * budget. No discovery happens when cached membership already exists.</p>
     */
    public AnimalGroup discoverOrCreate(
            PathfinderMob animal,
            SpeciesBehaviorProfile profile,
            int radius,
            int maximumSize,
            long validationInterval
    ) {
        Optional<AnimalGroup> existing = groupFor(animal);
        if (existing.isPresent()) {
            return existing.get();
        }
        if (!(animal.level() instanceof ServerLevel level)) {
            throw new IllegalArgumentException("Animal groups require a server-level entity");
        }
        if (!eligible(animal, profile)) {
            throw new IllegalArgumentException("Animal is not eligible for the supplied social profile");
        }

        LevelGroups levelGroups = levels.computeIfAbsent(level, ignored -> new LevelGroups());
        ResourceLocation entityTypeId = BuiltInRegistries.ENTITY_TYPE.getKey(animal.getType());
        int cappedSize = Math.max(1, maximumSize);
        List<PathfinderMob> nearby = candidateLocator.find(animal, Math.max(1, radius));

        // Prefer joining an established nearby group over creating overlapping groups.
        for (PathfinderMob candidate : nearby) {
            AnimalGroup candidateGroup = levelGroups.membership().get(candidate.getUUID());
            if (candidateGroup != null
                    && candidateGroup.memberCount() < cappedSize
                    && candidateGroup.matches(profile.id(), entityTypeId)
                    && eligible(animal, profile)) {
                candidateGroup.addMember(animal.getUUID());
                levelGroups.membership().put(animal.getUUID(), candidateGroup);
                return candidateGroup;
            }
        }

        Map<UUID, PathfinderMob> selected = new LinkedHashMap<>();
        selected.put(animal.getUUID(), animal);
        nearby.stream()
                .filter(candidate -> candidate.getType() == animal.getType())
                .filter(candidate -> eligible(candidate, profile))
                .filter(candidate -> !levelGroups.membership().containsKey(candidate.getUUID()))
                .sorted(Comparator
                        .comparingDouble((PathfinderMob candidate) -> animal.distanceToSqr(candidate))
                        .thenComparing(candidate -> candidate.getUUID().toString()))
                .limit(Math.max(0, cappedSize - 1L))
                .forEach(candidate -> selected.putIfAbsent(candidate.getUUID(), candidate));

        PathfinderMob leader = chooseLeader(selected.values());
        long gameTime = level.getGameTime();
        AnimalGroup group = new AnimalGroup(
                UUID.randomUUID(),
                profile.id(),
                entityTypeId,
                leader.getUUID(),
                nextValidationAt(gameTime, validationInterval, leader.getUUID())
        );
        for (PathfinderMob member : selected.values()) {
            group.addMember(member.getUUID());
            levelGroups.membership().put(member.getUUID(), group);
        }
        levelGroups.groups().put(group.id(), group);
        return group;
    }

    /**
     * Validates cached UUIDs, elects a loaded leader, and recruits locally on cooldown.
     *
     * <p>This method is intended to be called only by the current leader after
     * the ecosystem expensive-work budget has admitted the pass.</p>
     */
    public void validateAndRecruit(
            AnimalGroup group,
            SpeciesBehaviorProfile profile,
            int radius,
            int maximumSize,
            long validationInterval
    ) {
        ServerLevel level = levelFor(group);
        if (level == null) {
            return;
        }
        LevelGroups levelGroups = levels.get(level);
        if (levelGroups == null) {
            return;
        }

        List<PathfinderMob> loadedMembers = new ArrayList<>();
        for (UUID memberId : new ArrayList<>(group.members())) {
            PathfinderMob member = resolve(level, memberId);
            if (member == null || !eligible(member, profile)) {
                group.removeMember(memberId);
                levelGroups.membership().remove(memberId, group);
            } else {
                loadedMembers.add(member);
            }
        }
        if (loadedMembers.isEmpty()) {
            removeGroup(levelGroups, group);
            return;
        }
        if (!group.contains(group.getLeader())) {
            group.setLeader(chooseLeader(loadedMembers).getUUID());
        }

        // Recruitment is one cached local lookup per validation, not one lookup per member.
        PathfinderMob leader = resolve(level, group.getLeader());
        int cappedSize = Math.max(1, maximumSize);
        if (leader != null && group.memberCount() < cappedSize) {
            for (PathfinderMob candidate : candidateLocator.find(leader, Math.max(1, radius))) {
                if (group.memberCount() >= cappedSize) {
                    break;
                }
                if (candidate.getType() != leader.getType()
                        || levelGroups.membership().containsKey(candidate.getUUID())
                        || !eligible(candidate, profile)) {
                    continue;
                }
                group.addMember(candidate.getUUID());
                levelGroups.membership().put(candidate.getUUID(), group);
            }
        }
        group.scheduleValidation(nextValidationAt(
                level.getGameTime(), validationInterval, group.id()));
    }

    /** Resolves the current leader directly from the group's server level. */
    public Optional<PathfinderMob> resolveLeader(AnimalGroup group) {
        ServerLevel level = levelFor(group);
        return Optional.ofNullable(level == null ? null : resolve(level, group.getLeader()));
    }

    /** Resolves currently loaded members without scanning the level. */
    public List<PathfinderMob> resolveMembers(AnimalGroup group) {
        ServerLevel level = levelFor(group);
        if (level == null) {
            return List.of();
        }
        List<PathfinderMob> members = new ArrayList<>();
        for (UUID memberId : group.members()) {
            PathfinderMob member = resolve(level, memberId);
            if (member != null) {
                members.add(member);
            }
        }
        return List.copyOf(members);
    }

    /** Computes a centroid from cached loaded members for the existing social need model. */
    public Optional<EnvironmentalContext.HerdCenter> center(AnimalGroup group, PathfinderMob observer) {
        List<PathfinderMob> members = resolveMembers(group);
        if (members.size() <= 1) {
            return Optional.empty();
        }
        double x = 0.0;
        double y = 0.0;
        double z = 0.0;
        for (PathfinderMob member : members) {
            x += member.getX();
            y += member.getY();
            z += member.getZ();
        }
        BlockPos center = BlockPos.containing(x / members.size(), y / members.size(), z / members.size());
        return Optional.of(new EnvironmentalContext.HerdCenter(
                center,
                members.size(),
                observer.distanceToSqr(center.getCenter()),
                group.getLeader(),
                group.roleOf(observer.getUUID()).orElse(GroupRole.FOLLOWER) == GroupRole.LEADER
        ));
    }

    /**
     * Shares one serious threat and broad escape direction with cached members.
     *
     * <p>Member needs are woken directly by UUID. Followers then reuse the
     * leader-relative direction instead of running individual threat scans.</p>
     */
    public void reportThreat(
            PathfinderMob reporter,
            EnvironmentalContext.Threat threat,
            double formationRadius
    ) {
        Optional<AnimalGroup> resolvedGroup = groupFor(reporter);
        if (resolvedGroup.isEmpty() || !(reporter.level() instanceof ServerLevel level)) {
            return;
        }
        AnimalGroup group = resolvedGroup.get();
        Vec3 origin = center(group, reporter)
                .map(center -> center.position().getCenter())
                .orElse(reporter.position());
        Vec3 away = origin.subtract(threat.position().getCenter()).multiply(1.0, 0.0, 1.0);
        if (away.lengthSqr() < 1.0E-4) {
            double angle = Math.floorMod(group.id().getLeastSignificantBits(), 360L) * Math.PI / 180.0;
            away = new Vec3(Math.cos(angle), 0.0, Math.sin(angle));
        } else {
            away = away.normalize();
        }
        double escapeDistance = Math.max(12.0, formationRadius * 3.0);
        BlockPos escapeTarget = BlockPos.containing(origin.add(away.scale(escapeDistance)));
        group.reportThreat(threat.position(), threat.entityId(), escapeTarget);

        for (PathfinderMob member : resolveMembers(group)) {
            AnimalNeedsState needs = member.getData(ModAttachments.ANIMAL_NEEDS);
            needs.rememberThreat(threat.position(), threat.entityId(), threat.expiresAt());
            GroupMemberPlan plan = group.planFor(member.getUUID())
                    .orElseGet(() -> GroupFormationPlanner.planFor(member.getUUID(), 1));
            needs.scheduleEvaluation(level.getGameTime() + plan.reactionDelayTicks());
        }
    }

    /** Removes an unloading member and immediately elects from cached loaded members. */
    public void onEntityLeave(ServerLevel level, PathfinderMob animal) {
        LevelGroups levelGroups = levels.get(level);
        if (levelGroups == null) {
            return;
        }
        AnimalGroup group = levelGroups.membership().remove(animal.getUUID());
        if (group == null) {
            return;
        }
        boolean wasLeader = animal.getUUID().equals(group.getLeader());
        group.removeMember(animal.getUUID());
        if (group.memberCount() == 0) {
            removeGroup(levelGroups, group);
            return;
        }
        if (wasLeader) {
            List<PathfinderMob> candidates = resolveMembers(group);
            if (candidates.isEmpty()) {
                removeGroup(levelGroups, group);
            } else {
                group.setLeader(chooseLeader(candidates).getUUID());
            }
        }
    }

    /** Returns aggregate level diagnostics; this iterates groups only when an operator asks. */
    public Snapshot snapshot(ServerLevel level) {
        LevelGroups levelGroups = levels.get(level);
        if (levelGroups == null) {
            return new Snapshot(0, 0, 0);
        }
        int members = 0;
        int decisions = 0;
        long gameTime = level.getGameTime();
        for (AnimalGroup group : levelGroups.groups().values()) {
            members += group.memberCount();
            decisions += group.leaderDecisionsPerMinute(gameTime);
        }
        return new Snapshot(levelGroups.groups().size(), members, decisions);
    }

    /** Clears transient membership for one unloading server level. */
    public void clear(ServerLevel level) {
        LevelGroups removed = levels.remove(level);
        if (removed != null) {
            removed.groups().values().forEach(AnimalGroup::invalidate);
        }
    }

    /** Clears every transient group after profile or group configuration changes. */
    public void clearAll() {
        levels.values().forEach(level -> level.groups().values().forEach(AnimalGroup::invalidate));
        levels.clear();
    }

    private ServerLevel levelFor(AnimalGroup group) {
        for (Map.Entry<ServerLevel, LevelGroups> entry : levels.entrySet()) {
            if (entry.getValue().groups().get(group.id()) == group) {
                return entry.getKey();
            }
        }
        return null;
    }

    private static boolean eligible(PathfinderMob candidate, SpeciesBehaviorProfile profile) {
        if (!candidate.isAlive()
                || candidate.isRemoved()
                || candidate.isNoAi()
                || candidate.isPassenger()
                || candidate instanceof TamableAnimal tamable && tamable.isTame()) {
            return false;
        }
        Optional<SpeciesBehaviorProfile> candidateProfile = SpeciesBehaviorProfileManager.profileFor(candidate);
        return candidateProfile.isPresent()
                && candidateProfile.get().id().equals(profile.id())
                && candidateProfile.get().herd().enabled();
    }

    private static PathfinderMob chooseLeader(Iterable<PathfinderMob> candidates) {
        PathfinderMob best = null;
        for (PathfinderMob candidate : candidates) {
            if (best == null || betterLeader(candidate, best)) {
                best = candidate;
            }
        }
        if (best == null) {
            throw new IllegalArgumentException("Cannot elect a leader from no candidates");
        }
        return best;
    }

    private static boolean betterLeader(PathfinderMob candidate, PathfinderMob current) {
        boolean candidateAdult = !(candidate instanceof AgeableMob ageable) || !ageable.isBaby();
        boolean currentAdult = !(current instanceof AgeableMob ageable) || !ageable.isBaby();
        if (candidateAdult != currentAdult) {
            return candidateAdult;
        }
        double candidateHealth = candidate.getHealth() / Math.max(1.0F, candidate.getMaxHealth());
        double currentHealth = current.getHealth() / Math.max(1.0F, current.getMaxHealth());
        if (Math.abs(candidateHealth - currentHealth) > 1.0E-5) {
            return candidateHealth > currentHealth;
        }
        return candidate.getUUID().compareTo(current.getUUID()) < 0;
    }

    private static PathfinderMob resolve(ServerLevel level, UUID memberId) {
        Entity entity = level.getEntity(memberId);
        return entity instanceof PathfinderMob animal && animal.isAlive() ? animal : null;
    }

    private static long nextValidationAt(long gameTime, long interval, UUID source) {
        long boundedInterval = Math.max(1L, interval);
        long jitterBound = Math.max(1L, boundedInterval / 4L);
        long jitter = Math.floorMod(source.getLeastSignificantBits(), jitterBound);
        return gameTime + boundedInterval + jitter;
    }

    private static void removeGroup(LevelGroups levelGroups, AnimalGroup group) {
        levelGroups.groups().remove(group.id(), group);
        for (UUID memberId : group.members()) {
            levelGroups.membership().remove(memberId, group);
        }
    }

    /** Bounded local lookup used only during group creation and validation. */
    @FunctionalInterface
    public interface CandidateLocator {
        List<PathfinderMob> find(PathfinderMob animal, int radius);
    }

    /** Aggregate debug values for one server level. */
    public record Snapshot(int groupCount, int memberCount, int leaderDecisionsPerMinute) {
    }

    private record LevelGroups(
            Map<UUID, AnimalGroup> groups,
            Map<UUID, AnimalGroup> membership
    ) {
        private LevelGroups() {
            this(new HashMap<>(), new HashMap<>());
        }
    }
}
