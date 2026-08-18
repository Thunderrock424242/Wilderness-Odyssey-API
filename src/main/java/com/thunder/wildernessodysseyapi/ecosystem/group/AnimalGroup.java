package com.thunder.wildernessodysseyapi.ecosystem.group;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Transient server-owned identity and broad decision state for one animal group.
 *
 * <p>Groups cache UUID membership but do not tick themselves and are not saved.
 * The manager validates them only on cooldown or an entity lifecycle event.
 * Public request methods are intended to be called on the logical server
 * thread by weather, migration, disturbance, or future ecology systems.</p>
 */
public final class AnimalGroup {

    private static final int MAXIMUM_DECISION_SAMPLES = 128;
    private static final long DECISION_RATE_WINDOW_TICKS = 1_200L;

    private final UUID groupId;
    private final ResourceLocation profileId;
    private final ResourceLocation entityTypeId;
    private final Map<UUID, GroupMemberPlan> members = new LinkedHashMap<>();
    private final Deque<Long> leaderDecisionTicks = new ArrayDeque<>();
    private UUID leaderId;
    private GroupBehavior state = GroupBehavior.IDLE;
    private BlockPos destination;
    private BlockPos threatPosition;
    private UUID threatEntityId;
    private GroupBehavior requestedState;
    private BlockPos requestedDestination;
    private long revision;
    private long nextLeaderDecisionAt;
    private long nextValidationAt;
    private DecisionSource decisionSource = DecisionSource.LEADER;
    private int nextSlotIndex;

    AnimalGroup(
            UUID groupId,
            ResourceLocation profileId,
            ResourceLocation entityTypeId,
            UUID leaderId,
            long nextValidationAt
    ) {
        this.groupId = groupId;
        this.profileId = profileId;
        this.entityTypeId = entityTypeId;
        this.leaderId = leaderId;
        this.nextValidationAt = nextValidationAt;
        addMember(leaderId);
    }

    /** Returns the transient group identifier used by diagnostics and integrations. */
    public UUID id() {
        return groupId;
    }

    /** Returns the profile identity that makes these members behavior-compatible. */
    public ResourceLocation profileId() {
        return profileId;
    }

    /** Returns the current leader UUID; the manager can resolve it to a loaded entity. */
    public UUID getLeader() {
        return leaderId;
    }

    /** Returns an immutable snapshot of cached member UUIDs. */
    public Set<UUID> members() {
        return Set.copyOf(members.keySet());
    }

    /** Returns the cached member count without resolving or scanning entities. */
    public int memberCount() {
        return members.size();
    }

    /** Returns the currently published broad group activity. */
    public GroupBehavior state() {
        return state;
    }

    /** Returns the currently published broad destination, if any. */
    public BlockPos destination() {
        return destination;
    }

    /** Returns the latest serious threat position reported to the group. */
    public BlockPos threatPosition() {
        return threatPosition;
    }

    /** Returns the latest serious threat entity, when the report had one. */
    public UUID threatEntityId() {
        return threatEntityId;
    }

    /** Returns the role of a cached member, or empty when the UUID is not in this group. */
    public Optional<GroupRole> roleOf(UUID memberId) {
        if (!members.containsKey(memberId)) {
            return Optional.empty();
        }
        return Optional.of(memberId.equals(leaderId) ? GroupRole.LEADER : GroupRole.FOLLOWER);
    }

    /** Returns the stable loose-following plan for a cached member. */
    public Optional<GroupMemberPlan> planFor(UUID memberId) {
        return Optional.ofNullable(members.get(memberId));
    }

    /**
     * Requests a broad state for the leader to resolve on its next decision pass.
     *
     * <p>State-only water and shelter requests ask the existing ecosystem
     * controller to locate an authoritative destination.</p>
     */
    public void requestState(GroupBehavior requestedState) {
        this.requestedState = requestedState == null ? GroupBehavior.IDLE : requestedState;
        if (this.requestedState == GroupBehavior.IDLE) {
            requestedDestination = null;
        }
        revision++;
    }

    /** Requests a destination while preserving an explicitly requested state, if present. */
    public void requestDestination(BlockPos destination) {
        requestedDestination = immutable(destination);
        if (requestedState == null || requestedState == GroupBehavior.IDLE) {
            requestedState = requestedDestination == null ? GroupBehavior.IDLE : GroupBehavior.TRAVEL;
        }
        revision++;
    }

    /** Atomically requests a broad state and destination for the group leader. */
    public void requestState(GroupBehavior requestedState, BlockPos destination) {
        this.requestedState = requestedState == null ? GroupBehavior.IDLE : requestedState;
        this.requestedDestination = immutable(destination);
        revision++;
    }

    /**
     * Publishes immediate shared flight and queues the same direction for leader ownership.
     *
     * @param threatPosition location members should move away from
     * @param threatEntityId optional threat entity UUID
     * @param escapeTarget shared broad escape target selected by the threat system
     */
    public void reportThreat(BlockPos threatPosition, UUID threatEntityId, BlockPos escapeTarget) {
        this.threatPosition = immutable(threatPosition);
        this.threatEntityId = threatEntityId;
        this.state = GroupBehavior.FLEE;
        this.destination = immutable(escapeTarget);
        this.requestedState = GroupBehavior.FLEE;
        this.requestedDestination = immutable(escapeTarget);
        this.decisionSource = DecisionSource.THREAT;
        revision++;
    }

    /** Returns the number of leader decision passes observed in the last in-game minute. */
    public int leaderDecisionsPerMinute(long gameTime) {
        trimDecisionSamples(gameTime);
        return leaderDecisionTicks.size();
    }

    /** Returns the change revision used to stagger follower reactions. */
    public long revision() {
        return revision;
    }

    boolean matches(ResourceLocation profileId, ResourceLocation entityTypeId) {
        return this.profileId.equals(profileId) && this.entityTypeId.equals(entityTypeId);
    }

    boolean contains(UUID memberId) {
        return members.containsKey(memberId);
    }

    void addMember(UUID memberId) {
        members.computeIfAbsent(memberId, ignored ->
                GroupFormationPlanner.planFor(memberId, nextSlotIndex++));
    }

    void removeMember(UUID memberId) {
        members.remove(memberId);
    }

    void setLeader(UUID leaderId) {
        if (!members.containsKey(leaderId)) {
            throw new IllegalArgumentException("Group leader must be a member");
        }
        if (!leaderId.equals(this.leaderId)) {
            this.leaderId = leaderId;
            revision++;
        }
    }

    /** Returns whether an integration or threat has queued leader-owned work. */
    public boolean hasPendingLeaderRequest() {
        return requestedState != null;
    }

    /** Returns the queued broad state, or {@code null} when no request is pending. */
    public GroupBehavior requestedState() {
        return requestedState;
    }

    /** Returns the queued destination, if the integration supplied one. */
    public BlockPos requestedDestination() {
        return requestedDestination;
    }

    /** Returns whether a request or cooldown permits the next leader decision. */
    public boolean canLeaderDecide(long gameTime) {
        return hasPendingLeaderRequest() || gameTime >= nextLeaderDecisionAt;
    }

    /** Returns the earliest ordinary leader decision tick. */
    public long nextLeaderDecisionAt() {
        return nextLeaderDecisionAt;
    }

    /** Returns whether cached membership is due for a leader-owned validation pass. */
    public boolean validationDue(long gameTime) {
        return gameTime >= nextValidationAt;
    }

    void scheduleValidation(long gameTime) {
        nextValidationAt = Math.max(0L, gameTime);
    }

    void invalidate() {
        state = GroupBehavior.IDLE;
        destination = null;
        requestedState = null;
        requestedDestination = null;
        revision++;
    }

    /** Publishes one leader decision, clears its request, and returns the new revision. */
    public long publishLeaderDecision(
            GroupBehavior state,
            BlockPos destination,
            long gameTime,
            long decisionInterval
    ) {
        this.state = state == null ? GroupBehavior.IDLE : state;
        this.destination = immutable(destination);
        this.requestedState = null;
        this.requestedDestination = null;
        this.decisionSource = DecisionSource.LEADER;
        recordDecision(gameTime, decisionInterval);
        revision++;
        return revision;
    }

    /** Records a cooldown-gated leader search that found no usable requested target. */
    public void recordLeaderAttempt(long gameTime, long decisionInterval) {
        recordDecision(gameTime, decisionInterval);
    }

    /** Clears a completed leader decision unless a newer request has replaced it. */
    public void clearDecisionIfRevision(long expectedRevision) {
        if (revision != expectedRevision || decisionSource != DecisionSource.LEADER) {
            return;
        }
        state = GroupBehavior.IDLE;
        destination = null;
        revision++;
    }

    private void recordDecision(long gameTime, long decisionInterval) {
        nextLeaderDecisionAt = gameTime + Math.max(1L, decisionInterval);
        leaderDecisionTicks.addLast(gameTime);
        trimDecisionSamples(gameTime);
        while (leaderDecisionTicks.size() > MAXIMUM_DECISION_SAMPLES) {
            leaderDecisionTicks.removeFirst();
        }
    }

    private void trimDecisionSamples(long gameTime) {
        long earliest = gameTime - DECISION_RATE_WINDOW_TICKS;
        while (!leaderDecisionTicks.isEmpty() && leaderDecisionTicks.getFirst() < earliest) {
            leaderDecisionTicks.removeFirst();
        }
    }

    private static BlockPos immutable(BlockPos position) {
        return position == null ? null : position.immutable();
    }

    private enum DecisionSource {
        LEADER,
        THREAT
    }
}
