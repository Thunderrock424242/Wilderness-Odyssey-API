package com.thunder.wildernessodysseyapi.ecosystem.state;

import com.thunder.wildernessodysseyapi.ecosystem.api.EcosystemBehaviorState;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.neoforged.neoforge.common.util.INBTSerializable;

import java.util.UUID;

/**
 * Compact server-owned needs and transient decision state for one animal.
 *
 * <p>Only four normalized motivations and the hunting cooldown survive entity
 * saves. Paths, environment targets, debug timing, and short-lived threat
 * memory are rebuilt after load to keep entity NBT small and robust.</p>
 */
public final class AnimalNeedsState implements INBTSerializable<CompoundTag> {

    private static final int FORMAT_VERSION = 1;

    private float thirst;
    private float hunger;
    private float rest;
    private float social;
    private float safetyConcern;
    private long nextHuntAllowedAt;

    private EcosystemBehaviorState behavior = EcosystemBehaviorState.IDLE;
    private BlockPos behaviorTarget;
    private BlockPos waterPosition;
    private BlockPos shelterPosition;
    private BlockPos threatPosition;
    private UUID threatEntityId;
    private long threatExpiresAt;
    private UUID huntTargetId;
    private long nextEvaluationAt;
    private long actionStartedAt;
    private long shelterReleaseAt = Long.MAX_VALUE;
    private long lastEvaluationNanos;
    private long lastEvaluatedAt;
    private boolean controllerInstalled;

    /** Returns normalized thirst urgency, where one is critically thirsty. */
    public float thirst() {
        return thirst;
    }

    /** Returns normalized hunger urgency, where one is critically hungry. */
    public float hunger() {
        return hunger;
    }

    /** Returns normalized rest urgency. */
    public float rest() {
        return rest;
    }

    /** Returns normalized herd/social urgency. */
    public float social() {
        return social;
    }

    /** Returns transient normalized concern caused by a recent threat or disturbance. */
    public float safetyConcern() {
        return safetyConcern;
    }

    /** Replaces the persisted needs after one deterministic simulation step. */
    public void setNeeds(double thirst, double hunger, double rest, double social, double safetyConcern) {
        this.thirst = unit(thirst);
        this.hunger = unit(hunger);
        this.rest = unit(rest);
        this.social = unit(social);
        this.safetyConcern = unit(safetyConcern);
    }

    /** Reduces thirst after a completed drink without resetting unrelated needs. */
    public void restoreThirst(double amount) {
        thirst = unit(thirst - amount);
    }

    /** Satisfies predator hunger after the selected prey dies. */
    public void satisfyHunger(double remainingHunger) {
        hunger = unit(remainingHunger);
    }

    /** Returns the currently selected high-level behavior. */
    public EcosystemBehaviorState behavior() {
        return behavior;
    }

    /** Selects a new behavior and records its start time and optional navigation target. */
    public void begin(EcosystemBehaviorState behavior, BlockPos target, long gameTime) {
        this.behavior = behavior;
        this.behaviorTarget = target == null ? null : target.immutable();
        this.actionStartedAt = gameTime;
    }

    /** Clears the active behavior while retaining detected locations for diagnostics. */
    public void idle() {
        behavior = EcosystemBehaviorState.IDLE;
        behaviorTarget = null;
        huntTargetId = null;
        actionStartedAt = 0L;
        shelterReleaseAt = Long.MAX_VALUE;
    }

    public BlockPos behaviorTarget() {
        return behaviorTarget;
    }

    public long actionStartedAt() {
        return actionStartedAt;
    }

    public BlockPos waterPosition() {
        return waterPosition;
    }

    public void setWaterPosition(BlockPos waterPosition) {
        this.waterPosition = immutable(waterPosition);
    }

    public BlockPos shelterPosition() {
        return shelterPosition;
    }

    public void setShelterPosition(BlockPos shelterPosition) {
        this.shelterPosition = immutable(shelterPosition);
    }

    public BlockPos threatPosition() {
        return threatPosition;
    }

    public UUID threatEntityId() {
        return threatEntityId;
    }

    public long threatExpiresAt() {
        return threatExpiresAt;
    }

    /** Retains a threat long enough for flight to continue after line of sight is lost. */
    public void rememberThreat(BlockPos position, UUID entityId, long expiresAt) {
        threatPosition = immutable(position);
        threatEntityId = entityId;
        threatExpiresAt = Math.max(threatExpiresAt, expiresAt);
        safetyConcern = 1.0F;
    }

    /** Clears expired short-lived threat memory. */
    public void forgetThreat(long gameTime) {
        if (gameTime >= threatExpiresAt) {
            threatPosition = null;
            threatEntityId = null;
            threatExpiresAt = 0L;
        }
    }

    public UUID huntTargetId() {
        return huntTargetId;
    }

    public void setHuntTargetId(UUID huntTargetId) {
        this.huntTargetId = huntTargetId;
    }

    public long nextHuntAllowedAt() {
        return nextHuntAllowedAt;
    }

    public void setNextHuntAllowedAt(long nextHuntAllowedAt) {
        this.nextHuntAllowedAt = Math.max(0L, nextHuntAllowedAt);
    }

    public long nextEvaluationAt() {
        return nextEvaluationAt;
    }

    public void scheduleEvaluation(long gameTime) {
        nextEvaluationAt = Math.max(0L, gameTime);
    }

    /** Records one controller evaluation for diagnostics and future need integration. */
    public void recordEvaluation(long gameTime, long elapsedNanos, long nextEvaluationAt) {
        lastEvaluatedAt = gameTime;
        lastEvaluationNanos = Math.max(0L, elapsedNanos);
        this.nextEvaluationAt = Math.max(gameTime + 1L, nextEvaluationAt);
    }

    public long lastEvaluatedAt() {
        return lastEvaluatedAt;
    }

    public long lastEvaluationNanos() {
        return lastEvaluationNanos;
    }

    public long shelterReleaseAt() {
        return shelterReleaseAt;
    }

    public void setShelterReleaseAt(long shelterReleaseAt) {
        this.shelterReleaseAt = Math.max(0L, shelterReleaseAt);
    }

    public boolean controllerInstalled() {
        return controllerInstalled;
    }

    public void markControllerInstalled() {
        controllerInstalled = true;
    }

    @Override
    public CompoundTag serializeNBT(HolderLookup.Provider provider) {
        CompoundTag tag = new CompoundTag();
        tag.putInt("v", FORMAT_VERSION);
        tag.putFloat("thirst", thirst);
        tag.putFloat("hunger", hunger);
        tag.putFloat("rest", rest);
        tag.putFloat("social", social);
        tag.putLong("hunt_ready", nextHuntAllowedAt);
        return tag;
    }

    @Override
    public void deserializeNBT(HolderLookup.Provider provider, CompoundTag tag) {
        thirst = unit(tag.getFloat("thirst"));
        hunger = unit(tag.getFloat("hunger"));
        rest = unit(tag.getFloat("rest"));
        social = unit(tag.getFloat("social"));
        nextHuntAllowedAt = Math.max(0L, tag.getLong("hunt_ready"));

        // Transient decisions are intentionally rebuilt against the current world.
        safetyConcern = 0.0F;
        behavior = EcosystemBehaviorState.IDLE;
        behaviorTarget = null;
        waterPosition = null;
        shelterPosition = null;
        threatPosition = null;
        threatEntityId = null;
        huntTargetId = null;
        threatExpiresAt = 0L;
        nextEvaluationAt = 0L;
        actionStartedAt = 0L;
        shelterReleaseAt = Long.MAX_VALUE;
        controllerInstalled = false;
    }

    private static BlockPos immutable(BlockPos position) {
        return position == null ? null : position.immutable();
    }

    private static float unit(double value) {
        if (!Double.isFinite(value)) {
            return 0.0F;
        }
        return (float) Math.max(0.0, Math.min(1.0, value));
    }
}
