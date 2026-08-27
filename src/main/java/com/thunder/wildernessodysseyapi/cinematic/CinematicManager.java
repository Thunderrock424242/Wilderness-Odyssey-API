package com.thunder.wildernessodysseyapi.cinematic;

import com.thunder.wildernessodysseyapi.cinematic.network.CinematicStagePayload;
import com.thunder.wildernessodysseyapi.cinematic.network.CinematicNarrationPayload;
import com.thunder.wildernessodysseyapi.cinematic.network.EndCinematicPayload;
import com.thunder.wildernessodysseyapi.cinematic.network.StartCinematicPayload;
import com.thunder.wildernessodysseyapi.core.ModConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Server authority for registered per-player cinematic sessions.
 *
 * <p>The manager advances compact stage timelines on player ticks and sends
 * packets only when presentation state changes. It also owns the temporary
 * movement lock and restores it from every terminal path before releasing a
 * session.</p>
 */
public final class CinematicManager {
    private static final double MAX_LOCK_DRIFT_SQUARED = 0.0025D;
    private static final Map<UUID, RunningSequence> ACTIVE = new HashMap<>();
    private static final Map<ActorKey, UUID> ACTIVE_ACTORS = new HashMap<>();

    private CinematicManager() {
    }

    /** Starts a sequence definition with explicit server-owned playback options. */
    public static PlayResult play(
            ServerPlayer player,
            CinematicSequence sequence,
            CinematicPlaybackOptions options
    ) {
        if (player == null || sequence == null || options == null || !player.isAlive() || player.isRemoved()) {
            return PlayResult.failure(Component.literal("The player is not available for a cinematic."));
        }
        if (ACTIVE.containsKey(player.getUUID())) {
            return PlayResult.failure(Component.literal("A cinematic is already active for this player."));
        }
        if (!sequence.isPlaybackAllowed(player)) {
            return PlayResult.failure(Component.literal(
                    "This cinematic is available only in a private single-player world."
            ));
        }
        if (options.markCompletion()) {
            if (CinematicPlayerData.hasCompleted(player, sequence.id())) {
                return PlayResult.failure(Component.literal("That story cinematic is already complete."));
            }
            // Enroll only explicit automatic requests. If startup fails after this point,
            // the incomplete sequence is eligible for a safe retry on the next login.
            CinematicPlayerData.markAutomaticStarted(player, sequence.id());
        }

        Optional<Component> validationFailure;
        try {
            validationFailure = sequence.validateStart(player, options.anchor());
        } catch (RuntimeException exception) {
            ModConstants.LOGGER.error("Cinematic {} validation failed for {}",
                    sequence.id(), player.getGameProfile().getName(), exception);
            return PlayResult.failure(Component.literal("The cinematic could not validate its world state."));
        }
        if (validationFailure.isPresent()) {
            return PlayResult.failure(validationFailure.get());
        }

        ActorKey actorKey = new ActorKey(player.level().dimension(), options.anchor());
        if (sequence.requiresExclusiveAnchor() && ACTIVE_ACTORS.containsKey(actorKey)) {
            return PlayResult.retryableFailure(Component.literal("That cinematic actor is already in use."));
        }

        RunningSequence running;
        try {
            running = new RunningSequence(player, sequence, options);
        } catch (RuntimeException exception) {
            ModConstants.LOGGER.error("Cinematic {} has an invalid timeline", sequence.id(), exception);
            return PlayResult.failure(Component.literal("The cinematic definition is invalid."));
        }

        ACTIVE.put(player.getUUID(), running);
        if (sequence.requiresExclusiveAnchor()) {
            ACTIVE_ACTORS.put(actorKey, player.getUUID());
        }
        try {
            setServerLock(running, running.stage().locksControls());
            Vec3 position = running.lockedPosition;
            player.teleportTo(
                    player.serverLevel(),
                    position.x,
                    position.y,
                    position.z,
                    running.baseYaw,
                    running.basePitch
            );
            sequence.onStart(running);
            sequence.onStageStarted(running);
            sendStart(running);
            return PlayResult.success(Component.literal("Started cinematic " + sequence.id() + "."));
        } catch (RuntimeException exception) {
            ModConstants.LOGGER.error("Cinematic {} failed to start for {}",
                    sequence.id(), player.getGameProfile().getName(), exception);
            stop(player, CinematicStopReason.ERROR);
            return PlayResult.failure(Component.literal("The cinematic failed to start; player controls were restored."));
        }
    }

    /** Starts a registered sequence by id. */
    public static PlayResult play(
            ServerPlayer player,
            ResourceLocation sequenceId,
            CinematicPlaybackOptions options
    ) {
        return CinematicSequenceRegistry.get(sequenceId)
                .map(sequence -> play(player, sequence, options))
                .orElseGet(() -> PlayResult.failure(Component.literal("Unknown cinematic: " + sequenceId)));
    }

    /** Advances one active player session on the authoritative server thread. */
    public static void tick(ServerPlayer player) {
        RunningSequence running = ACTIVE.get(player.getUUID());
        if (running == null) {
            return;
        }
        if (running.player != player || !player.isAlive() || player.isRemoved()) {
            stop(running.player, CinematicStopReason.INVALID_STATE);
            return;
        }
        if (!player.level().dimension().equals(running.dimension)) {
            stop(player, CinematicStopReason.DIMENSION_CHANGED);
            return;
        }
        if (!running.sequence.isPlaybackAllowed(player)) {
            stop(player, CinematicStopReason.INVALID_STATE);
            return;
        }

        try {
            if (running.stage().locksControls()) {
                enforceServerLock(running);
            }
            running.sequence.onTick(running);
            CinematicTimeline.AdvanceResult result = running.timeline.advance();
            if (result == CinematicTimeline.AdvanceResult.COMPLETE) {
                stop(player, CinematicStopReason.COMPLETE);
                return;
            }
            if (result == CinematicTimeline.AdvanceResult.STAGE_CHANGED) {
                running.stageStartGameTime = player.level().getGameTime();
                setServerLock(running, running.stage().locksControls());
                running.sequence.onStageStarted(running);
                sendStage(running);
            }
        } catch (RuntimeException exception) {
            ModConstants.LOGGER.error("Cinematic {} failed during stage {} for {}",
                    running.sequence.id(), running.stage().id(), player.getGameProfile().getName(), exception);
            stop(player, CinematicStopReason.ERROR);
        }
    }

    /** Stops one active session and restores all server-owned temporary state. */
    public static boolean stop(ServerPlayer player, CinematicStopReason reason) {
        RunningSequence running = ACTIVE.remove(player.getUUID());
        if (running == null) {
            return false;
        }
        if (running.sequence.requiresExclusiveAnchor()) {
            ACTIVE_ACTORS.remove(new ActorKey(running.dimension, running.options.anchor()), player.getUUID());
        }

        boolean completed = reason == CinematicStopReason.COMPLETE;
        try {
            running.sequence.onStop(running, reason);
        } catch (RuntimeException exception) {
            ModConstants.LOGGER.error("Cinematic {} cleanup callback failed for {}",
                    running.sequence.id(), player.getGameProfile().getName(), exception);
        } finally {
            releaseServerLock(running);
            if (completed && running.options.markCompletion()) {
                CinematicPlayerData.markCompleted(player, running.sequence.id());
            }
            if (reason != CinematicStopReason.PLAYER_DISCONNECTED
                    && reason != CinematicStopReason.SERVER_STOPPING) {
                try {
                    PacketDistributor.sendToPlayer(
                            player,
                            new EndCinematicPayload(running.sequence.id(), completed)
                    );
                } catch (RuntimeException exception) {
                    ModConstants.LOGGER.warn("Could not send cinematic cleanup payload {} to {}",
                            running.sequence.id(), player.getGameProfile().getName(), exception);
                }
            }
        }
        return true;
    }

    /** Releases all process-local sessions before the server lifecycle ends. */
    public static void stopAll(MinecraftServer server) {
        for (RunningSequence running : new ArrayList<>(ACTIVE.values())) {
            stop(running.player, CinematicStopReason.SERVER_STOPPING);
        }
        ACTIVE.clear();
        ACTIVE_ACTORS.clear();
    }

    public static boolean isActive(ServerPlayer player) {
        return ACTIVE.containsKey(player.getUUID());
    }

    public static boolean controlsLocked(ServerPlayer player) {
        RunningSequence running = ACTIVE.get(player.getUUID());
        return running != null && running.stage().locksControls();
    }

    public static Optional<ResourceLocation> activeSequence(ServerPlayer player) {
        RunningSequence running = ACTIVE.get(player.getUUID());
        return running == null ? Optional.empty() : Optional.of(running.sequence.id());
    }

    private static void enforceServerLock(RunningSequence running) {
        ServerPlayer player = running.player;
        player.setDeltaMovement(Vec3.ZERO);
        player.setSprinting(false);
        player.stopUsingItem();
        player.fallDistance = 0.0F;
        if (player.position().distanceToSqr(running.lockedPosition) > MAX_LOCK_DRIFT_SQUARED) {
            // Correct only actual drift. Normal locked ticks never issue a teleport.
            player.teleportTo(
                    player.serverLevel(),
                    running.lockedPosition.x,
                    running.lockedPosition.y,
                    running.lockedPosition.z,
                    player.getYRot(),
                    player.getXRot()
            );
        }
    }

    private static void setServerLock(RunningSequence running, boolean locked) {
        if (locked == running.lockApplied) {
            return;
        }
        if (locked) {
            running.player.setNoGravity(true);
            running.player.setDeltaMovement(Vec3.ZERO);
            running.lockApplied = true;
        } else {
            releaseServerLock(running);
        }
    }

    private static void releaseServerLock(RunningSequence running) {
        if (!running.lockApplied) {
            return;
        }
        running.player.setNoGravity(running.originalNoGravity);
        running.player.setDeltaMovement(Vec3.ZERO);
        running.player.fallDistance = 0.0F;
        running.lockApplied = false;
    }

    private static void sendStart(RunningSequence running) {
        CinematicStage stage = running.stage();
        PacketDistributor.sendToPlayer(running.player, new StartCinematicPayload(
                running.sequence.id(),
                stage.id(),
                running.stageStartGameTime,
                stage.durationTicks(),
                stage.locksControls(),
                stage.hideHud(),
                running.options.anchor(),
                running.baseYaw,
                running.basePitch
        ));
    }

    private static void sendStage(RunningSequence running) {
        CinematicStage stage = running.stage();
        PacketDistributor.sendToPlayer(running.player, new CinematicStagePayload(
                running.sequence.id(),
                stage.id(),
                running.stageStartGameTime,
                stage.durationTicks(),
                stage.locksControls(),
                stage.hideHud()
        ));
    }

    /** Outcome returned to commands and automatic-spawn integration. */
    public record PlayResult(boolean started, boolean retryable, Component message) {
        private static PlayResult success(Component message) {
            return new PlayResult(true, false, message);
        }

        private static PlayResult failure(Component message) {
            return new PlayResult(false, false, message);
        }

        private static PlayResult retryableFailure(Component message) {
            return new PlayResult(false, true, message);
        }
    }

    private record ActorKey(ResourceKey<Level> dimension, BlockPos position) {
        private ActorKey {
            position = position.immutable();
        }
    }

    private static final class RunningSequence implements CinematicSequenceContext {
        private final ServerPlayer player;
        private final CinematicSequence sequence;
        private final CinematicPlaybackOptions options;
        private final CinematicTimeline timeline;
        private final ResourceKey<Level> dimension;
        private final Vec3 lockedPosition;
        private final float baseYaw;
        private final float basePitch;
        private final boolean originalNoGravity;
        private long stageStartGameTime;
        private boolean lockApplied;
        private final Set<ResourceLocation> narrationCues = new HashSet<>();

        private RunningSequence(
                ServerPlayer player,
                CinematicSequence sequence,
                CinematicPlaybackOptions options
        ) {
            this.player = player;
            this.sequence = sequence;
            this.options = options;
            this.timeline = new CinematicTimeline(sequence.stages());
            this.dimension = player.level().dimension();
            this.lockedPosition = sequence.lockedPosition(player, options.anchor());
            this.baseYaw = sequence.initialYaw(player, options.anchor());
            this.basePitch = sequence.initialPitch(player, options.anchor());
            this.originalNoGravity = player.isNoGravity();
            this.stageStartGameTime = player.level().getGameTime();
        }

        @Override
        public ServerPlayer player() {
            return player;
        }

        @Override
        public BlockPos anchor() {
            return options.anchor();
        }

        @Override
        public CinematicStage stage() {
            return timeline.stage();
        }

        @Override
        public int stageElapsedTicks() {
            return timeline.stageElapsedTicks();
        }

        @Override
        public boolean marksCompletion() {
            return options.markCompletion();
        }

        @Override
        public boolean cueActor(ResourceLocation cueId) {
            ServerLevel level = player.serverLevel();
            BlockEntity blockEntity = level.getBlockEntity(options.anchor());
            return blockEntity instanceof CinematicActor actor
                    && actor.applyCinematicCue(sequence.id(), cueId);
        }

        @Override
        public boolean narrateOnce(ResourceLocation cueId, int durationTicks) {
            if (!narrationCues.add(cueId)) {
                return false;
            }
            PacketDistributor.sendToPlayer(
                    player,
                    new CinematicNarrationPayload(sequence.id(), cueId, durationTicks)
            );
            return true;
        }
    }
}
