package com.thunder.wildernessodysseyapi.ecosystem.state;

import com.thunder.wildernessodysseyapi.ecosystem.api.EcosystemBehaviorState;
import com.thunder.wildernessodysseyapi.ecosystem.api.WeatherReactionDecision;
import com.thunder.wildernessodysseyapi.ecosystem.api.WildlifeSimulationLod;
import com.thunder.wildernessodysseyapi.ecosystem.api.WildlifeWeatherResponse;
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

    private static final int FORMAT_VERSION = 2;

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
    private long interactionStartedAt;
    private long shelterReleaseAt = Long.MAX_VALUE;
    private long lastEvaluationNanos;
    private long lastEvaluatedAt;
    private boolean controllerInstalled;
    private String decisionReason = "not evaluated";
    private WildlifeWeatherResponse weatherResponse = WildlifeWeatherResponse.NOT_SAMPLED;
    private String groupState = "not assessed";
    private WildlifeSimulationLod simulationLod = WildlifeSimulationLod.DORMANT;
    private WeatherReactionDecision weatherReaction = WeatherReactionDecision.NONE;
    private boolean simulationAiSuspended;
    private boolean simulationOriginalNoAi;

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

    /** Reduces hunger after a successful non-destructive forage action. */
    public void restoreHunger(double amount) {
        hunger = unit(hunger - amount);
    }

    /** Reduces accumulated rest need after a rest or sleep action. */
    public void recoverRest(double amount) {
        rest = unit(rest - amount);
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
        this.interactionStartedAt = 0L;
    }

    /** Selects a state and publishes the transient diagnostics used by operators. */
    public void begin(
            EcosystemBehaviorState behavior,
            BlockPos target,
            long gameTime,
            String reason,
            WildlifeWeatherResponse weatherResponse,
            String groupState,
            WildlifeSimulationLod simulationLod
    ) {
        begin(behavior, target, gameTime);
        setDiagnostics(reason, weatherResponse, groupState, simulationLod);
    }

    /** Clears the active behavior while retaining detected locations for diagnostics. */
    public void idle() {
        behavior = EcosystemBehaviorState.IDLE;
        behaviorTarget = null;
        huntTargetId = null;
        actionStartedAt = 0L;
        interactionStartedAt = 0L;
        shelterReleaseAt = Long.MAX_VALUE;
    }

    /** Records a non-pathfinding abstract state for DISTANT simulation. */
    public void abstractState(
            EcosystemBehaviorState behavior,
            long gameTime,
            String reason,
            WildlifeSimulationLod simulationLod
    ) {
        this.behavior = behavior;
        this.behaviorTarget = null;
        this.huntTargetId = null;
        this.actionStartedAt = gameTime;
        this.interactionStartedAt = 0L;
        setDiagnostics(
                reason,
                WildlifeWeatherResponse.NOT_SAMPLED,
                "abstract ecosystem simulation",
                simulationLod
        );
    }

    /** Updates diagnostic context without changing the selected state. */
    public void setDiagnostics(
            String reason,
            WildlifeWeatherResponse weatherResponse,
            String groupState,
            WildlifeSimulationLod simulationLod
    ) {
        this.decisionReason = safeText(reason, "unspecified");
        this.weatherResponse = weatherResponse == null
                ? WildlifeWeatherResponse.NOT_SAMPLED
                : weatherResponse;
        this.groupState = safeText(groupState, "individual");
        this.simulationLod = simulationLod == null ? WildlifeSimulationLod.DORMANT : simulationLod;
    }

    public BlockPos behaviorTarget() {
        return behaviorTarget;
    }

    public long actionStartedAt() {
        return actionStartedAt;
    }

    public long interactionStartedAt() {
        return interactionStartedAt;
    }

    /** Marks arrival at a drink or shelter destination without changing broad state. */
    public void markInteractionStarted(long gameTime) {
        if (interactionStartedAt == 0L) {
            interactionStartedAt = Math.max(1L, gameTime);
        }
    }

    public String decisionReason() {
        return decisionReason;
    }

    public WildlifeWeatherResponse weatherResponse() {
        return weatherResponse;
    }

    public String groupState() {
        return groupState;
    }

    public WildlifeSimulationLod simulationLod() {
        return simulationLod;
    }

    /** Updates only the cached distance tier without replacing behavior decision diagnostics. */
    public void setSimulationLod(WildlifeSimulationLod simulationLod) {
        this.simulationLod = simulationLod == null ? WildlifeSimulationLod.DORMANT : simulationLod;
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

    /** Returns whether the zone manager, rather than another system, currently owns the NoAI flag. */
    public boolean simulationAiSuspended() {
        return simulationAiSuspended;
    }

    /**
     * Records NoAI ownership before a distant entity enters the abstraction queue.
     *
     * <p>Minecraft persists NoAI, so this marker is persisted as well. A server
     * restart can then restore the exact state that existed before suspension.</p>
     */
    public void suspendAiForSimulation(boolean originalNoAi) {
        if (!simulationAiSuspended) {
            simulationAiSuspended = true;
            simulationOriginalNoAi = originalNoAi;
        }
    }

    /** Clears zone ownership and returns the NoAI value that existed before suspension. */
    public boolean resumeAiFromSimulation() {
        boolean original = simulationOriginalNoAi;
        simulationAiSuspended = false;
        simulationOriginalNoAi = false;
        return original;
    }

    /** Stores the latest transient incoming-weather decision for behavior and diagnostics. */
    public void rememberWeatherReaction(WeatherReactionDecision weatherReaction) {
        this.weatherReaction = weatherReaction == null ? WeatherReactionDecision.NONE : weatherReaction;
    }

    /** Returns the latest individual or inherited group weather decision. */
    public WeatherReactionDecision weatherReaction() {
        return weatherReaction;
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
        if (simulationAiSuspended) {
            tag.putBoolean("zone_ai_suspended", true);
            tag.putBoolean("zone_original_no_ai", simulationOriginalNoAi);
        }
        return tag;
    }

    @Override
    public void deserializeNBT(HolderLookup.Provider provider, CompoundTag tag) {
        thirst = unit(tag.getFloat("thirst"));
        hunger = unit(tag.getFloat("hunger"));
        rest = unit(tag.getFloat("rest"));
        social = unit(tag.getFloat("social"));
        nextHuntAllowedAt = Math.max(0L, tag.getLong("hunt_ready"));
        simulationAiSuspended = tag.getBoolean("zone_ai_suspended");
        simulationOriginalNoAi = simulationAiSuspended && tag.getBoolean("zone_original_no_ai");

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
        interactionStartedAt = 0L;
        shelterReleaseAt = Long.MAX_VALUE;
        lastEvaluationNanos = 0L;
        lastEvaluatedAt = 0L;
        controllerInstalled = false;
        decisionReason = "not evaluated";
        weatherResponse = WildlifeWeatherResponse.NOT_SAMPLED;
        groupState = "not assessed";
        simulationLod = WildlifeSimulationLod.DORMANT;
        weatherReaction = WeatherReactionDecision.NONE;
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

    private static String safeText(String value, String fallback) {
        if (value == null || value.isBlank()) {
            return fallback;
        }
        return value.length() <= 160 ? value : value.substring(0, 160);
    }
}
