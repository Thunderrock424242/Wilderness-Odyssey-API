package com.thunder.wildernessodysseyapi.cinematic.sequence;

import com.thunder.wildernessodysseyapi.cinematic.CinematicActor;
import com.thunder.wildernessodysseyapi.cinematic.CinematicControlPolicy;
import com.thunder.wildernessodysseyapi.cinematic.CinematicSequence;
import com.thunder.wildernessodysseyapi.cinematic.CinematicSequenceContext;
import com.thunder.wildernessodysseyapi.cinematic.CinematicStage;
import com.thunder.wildernessodysseyapi.cinematic.CinematicStopReason;
import com.thunder.wildernessodysseyapi.core.ModConstants;
import com.thunder.wildernessodysseyapi.core.ModRegistries;
import com.thunder.wildernessodysseyapi.core.PrivateSingleplayerPolicy;
import com.thunder.wildernessodysseyapi.cryo.block.CryoTubeBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Single-player cryogenic revival followed by an unlocked A.E.T.H.E.R recovery briefing.
 *
 * <p>The first ninety-one seconds are a controlled cinematic. The final twenty-five
 * seconds retain only presentation and recovery effects, allowing the player
 * to walk while A.E.T.H.E.R completes the introduction.</p>
 */
public final class CryoWakeupSequence implements CinematicSequence {
    public static final ResourceLocation ID = id("cryo_wakeup");

    public static final ResourceLocation BLACK_SCREEN = stageId("black_screen");
    public static final ResourceLocation EXTERIOR_REVEAL = stageId("exterior_reveal");
    public static final ResourceLocation MEDICAL_DIAGNOSTIC = stageId("medical_diagnostic");
    public static final ResourceLocation REVIVAL_PROTOCOL = stageId("revival_protocol");
    public static final ResourceLocation CARDIAC_PACING = stageId("cardiac_pacing");
    public static final ResourceLocation SUSPENSION_DRAIN = stageId("suspension_drain");
    public static final ResourceLocation BLACKOUT_TRANSITION = stageId("blackout_transition");
    public static final ResourceLocation EYES_REOPENING = stageId("eyes_reopening");
    public static final ResourceLocation MASK_RELEASE = stageId("mask_release");
    public static final ResourceLocation CRYO_OPENING = stageId("cryo_opening");
    public static final ResourceLocation BALANCE_CHECK = stageId("balance_check");
    public static final ResourceLocation RECOVERY_WALK = stageId("recovery_walk");

    public static final ResourceLocation CUE_IDLE = actorCue("idle");
    public static final ResourceLocation CUE_SUSPENDED = actorCue("suspended");
    public static final ResourceLocation CUE_DIAGNOSTIC = actorCue("diagnostic");
    public static final ResourceLocation CUE_REWARMING = actorCue("rewarming");
    public static final ResourceLocation CUE_CARDIAC_PACING = actorCue("cardiac_pacing");
    public static final ResourceLocation CUE_DRAINING = actorCue("draining");
    public static final ResourceLocation CUE_MASK_RELEASE = actorCue("mask_release");
    public static final ResourceLocation CUE_OPENING = actorCue("opening");
    public static final ResourceLocation CUE_OPEN = actorCue("open");

    public static final ResourceLocation NARRATION_MEDICAL_ONLINE = narration("medical_online");
    public static final ResourceLocation NARRATION_OCCUPANT_DETECTED = narration("occupant_detected");
    public static final ResourceLocation NARRATION_CONTAMINATION = narration("contamination");
    public static final ResourceLocation NARRATION_FILTRATION_OFFLINE = narration("filtration_offline");
    public static final ResourceLocation NARRATION_REVIVAL_AUTHORIZED = narration("revival_authorized");
    public static final ResourceLocation NARRATION_THERMAL_RESTORATION = narration("thermal_restoration");
    public static final ResourceLocation NARRATION_CIRCULATORY_ASSIST = narration("circulatory_assist");
    public static final ResourceLocation NARRATION_CRYOPROTECTANT_PURGE = narration("cryoprotectant_purge");
    public static final ResourceLocation NARRATION_REANIMATION_COMPOUND = narration("reanimation_compound");
    public static final ResourceLocation NARRATION_CARDIAC_LOW = narration("cardiac_low");
    public static final ResourceLocation NARRATION_PACING = narration("pacing");
    public static final ResourceLocation NARRATION_RHYTHM_RESTORED = narration("rhythm_restored");
    public static final ResourceLocation NARRATION_RESPIRATORY_RESPONSE = narration("respiratory_response");
    public static final ResourceLocation NARRATION_DRAINING = narration("draining");
    public static final ResourceLocation NARRATION_MASK_RELEASING = narration("mask_releasing");
    public static final ResourceLocation NARRATION_MOVE_SLOWLY = narration("move_slowly");
    public static final ResourceLocation NARRATION_AWAKE = narration("awake");
    public static final ResourceLocation NARRATION_AETHER_IDENTITY = narration("aether_identity");
    public static final ResourceLocation NARRATION_AETHER_LIMITS = narration("aether_limits");
    public static final ResourceLocation NARRATION_FIND_EXIT = narration("find_exit");

    private static final Map<ResourceLocation, Integer> NARRATION_DURATIONS = Map.ofEntries(
            Map.entry(NARRATION_MEDICAL_ONLINE, 160),
            Map.entry(NARRATION_OCCUPANT_DETECTED, 159),
            Map.entry(NARRATION_CONTAMINATION, 191),
            Map.entry(NARRATION_FILTRATION_OFFLINE, 117),
            Map.entry(NARRATION_REVIVAL_AUTHORIZED, 136),
            Map.entry(NARRATION_THERMAL_RESTORATION, 122),
            Map.entry(NARRATION_CIRCULATORY_ASSIST, 95),
            Map.entry(NARRATION_CRYOPROTECTANT_PURGE, 98),
            Map.entry(NARRATION_REANIMATION_COMPOUND, 122),
            Map.entry(NARRATION_CARDIAC_LOW, 165),
            Map.entry(NARRATION_PACING, 158),
            Map.entry(NARRATION_RHYTHM_RESTORED, 53),
            Map.entry(NARRATION_RESPIRATORY_RESPONSE, 57),
            Map.entry(NARRATION_DRAINING, 211),
            Map.entry(NARRATION_MASK_RELEASING, 124),
            Map.entry(NARRATION_MOVE_SLOWLY, 107),
            Map.entry(NARRATION_AWAKE, 113),
            Map.entry(NARRATION_AETHER_IDENTITY, 124),
            Map.entry(NARRATION_AETHER_LIMITS, 141),
            Map.entry(NARRATION_FIND_EXIT, 150)
    );
    private static final Map<ResourceLocation, List<NarrationCue>> STAGE_NARRATION = Map.ofEntries(
            Map.entry(EXTERIOR_REVEAL, List.of(cueAt(4, NARRATION_MEDICAL_ONLINE))),
            Map.entry(MEDICAL_DIAGNOSTIC, List.of(
                    cueAt(0, NARRATION_OCCUPANT_DETECTED),
                    cueAt(170, NARRATION_CONTAMINATION)
            )),
            Map.entry(REVIVAL_PROTOCOL, List.of(
                    cueAt(0, NARRATION_REVIVAL_AUTHORIZED),
                    cueAt(148, NARRATION_CRYOPROTECTANT_PURGE),
                    cueAt(258, NARRATION_REANIMATION_COMPOUND)
            )),
            Map.entry(CARDIAC_PACING, List.of(cueAt(0, NARRATION_PACING))),
            Map.entry(SUSPENSION_DRAIN, List.of(cueAt(0, NARRATION_DRAINING))),
            Map.entry(EYES_REOPENING, List.of(cueAt(4, NARRATION_AWAKE))),
            Map.entry(MASK_RELEASE, List.of(cueAt(0, NARRATION_MASK_RELEASING))),
            Map.entry(BALANCE_CHECK, List.of(cueAt(0, NARRATION_MOVE_SLOWLY)))
    );
    private static final List<CinematicStage> STAGES = List.of(
            stage(BLACK_SCREEN, 20, CinematicControlPolicy.LOCKED),
            stage(EXTERIOR_REVEAL, 170, CinematicControlPolicy.LOCKED),
            stage(MEDICAL_DIAGNOSTIC, 370, CinematicControlPolicy.LOCKED),
            stage(REVIVAL_PROTOCOL, 390, CinematicControlPolicy.LOCKED),
            stage(CARDIAC_PACING, 180, CinematicControlPolicy.LOCKED),
            stage(SUSPENSION_DRAIN, 220, CinematicControlPolicy.LOCKED),
            stage(BLACKOUT_TRANSITION, 20, CinematicControlPolicy.LOCKED),
            stage(EYES_REOPENING, 130, CinematicControlPolicy.LOCKED),
            stage(MASK_RELEASE, 130, CinematicControlPolicy.LOCKED),
            stage(CRYO_OPENING, 60, CinematicControlPolicy.LOCKED),
            stage(BALANCE_CHECK, 120, CinematicControlPolicy.LOCKED),
            stage(RECOVERY_WALK, 500, CinematicControlPolicy.PRESENTATION_ONLY)
    );

    @Override
    public ResourceLocation id() {
        return ID;
    }

    @Override
    public List<CinematicStage> stages() {
        return STAGES;
    }

    @Override
    public boolean isPlaybackAllowed(ServerPlayer player) {
        return PrivateSingleplayerPolicy.permits(player.server);
    }

    @Override
    public Optional<Component> validateStart(ServerPlayer player, BlockPos anchor) {
        if (!isPlaybackAllowed(player)) {
            return Optional.of(Component.literal(
                    "The cryo awakening is available only in a private single-player world."
            ));
        }
        ServerLevel level = player.serverLevel();
        if (!level.hasChunkAt(anchor)) {
            return Optional.of(Component.literal("The cryo tube chunk is not loaded."));
        }
        if (!level.getBlockState(anchor).is(CryoTubeBlock.CRYO_TUBE.get())) {
            return Optional.of(Component.literal("No cryo tube exists at the cinematic anchor."));
        }
        BlockEntity blockEntity = level.getBlockEntity(anchor);
        if (!(blockEntity instanceof CinematicActor)) {
            return Optional.of(Component.literal("The cryo tube animation hook is unavailable."));
        }
        return Optional.empty();
    }

    @Override
    public Optional<BlockPos> findDeveloperAnchor(ServerPlayer player) {
        BlockPos origin = player.blockPosition();
        BlockPos nearest = null;
        double nearestDistance = Double.MAX_VALUE;
        for (int y = -2; y <= 2; y++) {
            for (int x = -3; x <= 3; x++) {
                for (int z = -3; z <= 3; z++) {
                    BlockPos candidate = origin.offset(x, y, z);
                    if (!player.serverLevel().getBlockState(candidate).is(CryoTubeBlock.CRYO_TUBE.get())) {
                        continue;
                    }
                    double distance = candidate.distSqr(origin);
                    if (distance < nearestDistance) {
                        nearest = candidate.immutable();
                        nearestDistance = distance;
                    }
                }
            }
        }
        return Optional.ofNullable(nearest);
    }

    @Override
    public boolean requiresExclusiveAnchor() {
        return true;
    }

    @Override
    public Vec3 lockedPosition(ServerPlayer player, BlockPos anchor) {
        // Keep the authoritative body at the previously safe spawn position.
        // The client camera and presentation-only occupant have independent anchors.
        return new Vec3(anchor.getX() + 0.5D, anchor.getY() + 0.5D, anchor.getZ() + 0.5D);
    }

    @Override
    public float initialYaw(ServerPlayer player, BlockPos anchor) {
        return facing(player.serverLevel().getBlockState(anchor)).toYRot();
    }

    @Override
    public float initialPitch(ServerPlayer player, BlockPos anchor) {
        return -3.0F;
    }

    @Override
    public void onStart(CinematicSequenceContext context) {
        context.cueActor(CUE_SUSPENDED);
    }

    @Override
    public void onStageStarted(CinematicSequenceContext context) {
        ResourceLocation stage = context.stage().id();
        if (stage.equals(BLACK_SCREEN)) {
            sound(context, SoundEvents.CONDUIT_AMBIENT, 0.18F, 0.48F);
        } else if (stage.equals(EXTERIOR_REVEAL)) {
            context.cueActor(CUE_SUSPENDED);
            sound(context, SoundEvents.BEACON_AMBIENT, 0.30F, 0.60F);
        } else if (stage.equals(MEDICAL_DIAGNOSTIC)) {
            context.cueActor(CUE_DIAGNOSTIC);
            sound(context, SoundEvents.BEACON_POWER_SELECT, 0.34F, 1.30F);
        } else if (stage.equals(REVIVAL_PROTOCOL)) {
            context.cueActor(CUE_REWARMING);
            sound(context, SoundEvents.CONDUIT_AMBIENT, 0.34F, 0.72F);
        } else if (stage.equals(CARDIAC_PACING)) {
            context.cueActor(CUE_CARDIAC_PACING);
            sound(context, SoundEvents.BEACON_DEACTIVATE, 0.50F, 1.55F);
        } else if (stage.equals(SUSPENSION_DRAIN)) {
            context.cueActor(CUE_DRAINING);
            spawnMist(context);
            sound(context, SoundEvents.FIRE_EXTINGUISH, 0.65F, 0.62F);
        } else if (stage.equals(MASK_RELEASE)) {
            context.cueActor(CUE_MASK_RELEASE);
            sound(context, SoundEvents.IRON_TRAPDOOR_OPEN, 0.44F, 0.82F);
        } else if (stage.equals(CRYO_OPENING)) {
            context.cueActor(CUE_OPENING);
            sound(context, SoundEvents.PISTON_EXTEND, 0.58F, 0.72F);
        } else if (stage.equals(RECOVERY_WALK)) {
            context.cueActor(CUE_OPEN);
            releasePlayer(context);
            applyRecoveryEffects(context.player());
        }
    }

    @Override
    public void onTick(CinematicSequenceContext context) {
        ResourceLocation stage = context.stage().id();
        int elapsed = context.stageElapsedTicks();
        tickMedicalSounds(context, stage, elapsed);
        tickStageNarration(context, stage, elapsed);
        if (stage.equals(CARDIAC_PACING)) {
            if (elapsed == 135) {
                sound(context, SoundEvents.LIGHTNING_BOLT_IMPACT, 0.38F, 1.35F);
            }
        } else if (stage.equals(RECOVERY_WALK)) {
            tickRecoveryNarration(context, elapsed);
        }
    }

    @Override
    public void onStop(CinematicSequenceContext context, CinematicStopReason reason) {
        context.cueActor(reason == CinematicStopReason.COMPLETE ? CUE_OPEN : CUE_IDLE);
    }

    /** Returns the centralized start tick of a stage for tests and documentation. */
    public static int startTick(ResourceLocation stageId) {
        int tick = 0;
        for (CinematicStage stage : STAGES) {
            if (stage.id().equals(stageId)) {
                return tick;
            }
            tick += stage.durationTicks();
        }
        return -1;
    }

    public static int totalDurationTicks() {
        return STAGES.stream().mapToInt(CinematicStage::durationTicks).sum();
    }

    /** Returns one registered stage duration for client telemetry and tests. */
    public static int durationTicks(ResourceLocation stageId) {
        for (CinematicStage stage : STAGES) {
            if (stage.id().equals(stageId)) {
                return stage.durationTicks();
            }
        }
        return 0;
    }

    /** Returns the measured authored clip length, including a short subtitle fade tail. */
    public static int narrationDurationTicks(ResourceLocation cueId) {
        return NARRATION_DURATIONS.getOrDefault(cueId, 100);
    }

    static List<NarrationCue> narrationCues(ResourceLocation stageId) {
        return STAGE_NARRATION.getOrDefault(stageId, List.of());
    }

    private static void tickRecoveryNarration(CinematicSequenceContext context, int elapsed) {
        double distanceSquared = context.player().position().distanceToSqr(Vec3.atCenterOf(context.anchor()));
        if (elapsed >= 20 && (distanceSquared >= 2.25D || elapsed >= 40)) {
            context.narrateOnce(
                    NARRATION_AETHER_IDENTITY,
                    narrationDurationTicks(NARRATION_AETHER_IDENTITY)
            );
        }
        if (elapsed >= 180 && (distanceSquared >= 9.0D || elapsed >= 190)) {
            context.narrateOnce(
                    NARRATION_AETHER_LIMITS,
                    narrationDurationTicks(NARRATION_AETHER_LIMITS)
            );
        }
        if (elapsed >= 345 && (distanceSquared >= 25.0D || elapsed >= 350)) {
            context.narrateOnce(
                    NARRATION_FIND_EXIT,
                    narrationDurationTicks(NARRATION_FIND_EXIT)
            );
        }
    }

    private static void releasePlayer(CinematicSequenceContext context) {
        ServerPlayer player = context.player();
        Direction facing = facing(player.serverLevel().getBlockState(context.anchor()));
        Vec3 exit = Vec3.atCenterOf(context.anchor()).add(
                facing.getStepX() * 1.35D,
                0.0D,
                facing.getStepZ() * 1.35D
        );
        player.teleportTo(
                player.serverLevel(),
                exit.x,
                context.anchor().getY() + 0.5D,
                exit.z,
                facing.toYRot(),
                0.0F
        );
    }

    private static void applyRecoveryEffects(ServerPlayer player) {
        player.addEffect(new MobEffectInstance(
                ModRegistries.CRYO_SHAKES_EFFECT,
                400,
                0,
                true,
                false,
                true
        ));
        player.addEffect(new MobEffectInstance(
                MobEffects.MOVEMENT_SLOWDOWN,
                120,
                0,
                true,
                false,
                false
        ));
    }

    private static void tickStageNarration(
            CinematicSequenceContext context,
            ResourceLocation stage,
            int elapsed
    ) {
        for (NarrationCue cue : narrationCues(stage)) {
            if (elapsed == cue.tick()) {
                context.narrateOnce(cue.id(), narrationDurationTicks(cue.id()));
            }
        }
    }

    /** Adds sparse, stage-synchronized medical and machinery layers without a ticking sound loop. */
    private static void tickMedicalSounds(
            CinematicSequenceContext context,
            ResourceLocation stage,
            int elapsed
    ) {
        if (stage.equals(EXTERIOR_REVEAL)) {
            soundAt(context, elapsed, 26, SoundEvents.EXPERIENCE_ORB_PICKUP, 0.18F, 0.72F);
            soundAt(context, elapsed, 62, SoundEvents.EXPERIENCE_ORB_PICKUP, 0.18F, 0.84F);
        } else if (stage.equals(MEDICAL_DIAGNOSTIC)) {
            soundAt(context, elapsed, 12, SoundEvents.EXPERIENCE_ORB_PICKUP, 0.22F, 0.64F);
            soundAt(context, elapsed, 62, SoundEvents.EXPERIENCE_ORB_PICKUP, 0.22F, 0.68F);
            soundAt(context, elapsed, 112, SoundEvents.EXPERIENCE_ORB_PICKUP, 0.22F, 0.72F);
        } else if (stage.equals(REVIVAL_PROTOCOL)) {
            soundAt(context, elapsed, 10, SoundEvents.BREWING_STAND_BREW, 0.20F, 0.56F);
            soundAt(context, elapsed, 54, SoundEvents.PISTON_CONTRACT, 0.18F, 0.62F);
            soundAt(context, elapsed, 98, SoundEvents.BREWING_STAND_BREW, 0.20F, 0.66F);
            soundAt(context, elapsed, 142, SoundEvents.PISTON_CONTRACT, 0.18F, 0.70F);
            soundAt(context, elapsed, 178, SoundEvents.DISPENSER_DISPENSE, 0.28F, 1.18F);
        } else if (stage.equals(CARDIAC_PACING)) {
            soundAt(context, elapsed, 8, SoundEvents.WARDEN_HEARTBEAT, 0.22F, 1.18F);
            soundAt(context, elapsed, 28, SoundEvents.WARDEN_HEARTBEAT, 0.22F, 1.24F);
            soundAt(context, elapsed, 60, SoundEvents.EXPERIENCE_ORB_PICKUP, 0.25F, 1.10F);
            soundAt(context, elapsed, 72, SoundEvents.EXPERIENCE_ORB_PICKUP, 0.25F, 1.18F);
        } else if (stage.equals(SUSPENSION_DRAIN)) {
            soundAt(context, elapsed, 8, SoundEvents.EXPERIENCE_ORB_PICKUP, 0.20F, 1.16F);
            soundAt(context, elapsed, 32, SoundEvents.BUBBLE_COLUMN_WHIRLPOOL_AMBIENT, 0.22F, 0.66F);
            soundAt(context, elapsed, 40, SoundEvents.EXPERIENCE_ORB_PICKUP, 0.20F, 1.20F);
            soundAt(context, elapsed, 64, SoundEvents.BUBBLE_COLUMN_WHIRLPOOL_AMBIENT, 0.22F, 0.72F);
            soundAt(context, elapsed, 72, SoundEvents.EXPERIENCE_ORB_PICKUP, 0.20F, 1.22F);
        } else if (stage.equals(EYES_REOPENING)) {
            soundAt(context, elapsed, 18, SoundEvents.PLAYER_BREATH, 0.28F, 0.72F);
            soundAt(context, elapsed, 56, SoundEvents.PLAYER_BREATH, 0.30F, 0.78F);
        } else if (stage.equals(MASK_RELEASE)) {
            soundAt(context, elapsed, 12, SoundEvents.PLAYER_BREATH, 0.34F, 0.84F);
        } else if (stage.equals(CRYO_OPENING)) {
            soundAt(context, elapsed, 18, SoundEvents.PISTON_CONTRACT, 0.24F, 0.64F);
        } else if (stage.equals(BALANCE_CHECK)) {
            soundAt(context, elapsed, 10, SoundEvents.PLAYER_BREATH, 0.24F, 0.88F);
            soundAt(context, elapsed, 30, SoundEvents.PLAYER_BREATH, 0.22F, 0.94F);
        }
    }

    private static void soundAt(
            CinematicSequenceContext context,
            int elapsed,
            int cueTick,
            SoundEvent sound,
            float volume,
            float pitch
    ) {
        if (elapsed == cueTick) {
            sound(context, sound, volume, pitch);
        }
    }

    private static void spawnMist(CinematicSequenceContext context) {
        BlockPos anchor = context.anchor();
        context.player().serverLevel().sendParticles(
                ParticleTypes.CLOUD,
                anchor.getX() + 0.5D,
                anchor.getY() + 0.9D,
                anchor.getZ() + 0.5D,
                24,
                0.40D,
                0.80D,
                0.40D,
                0.025D
        );
    }

    private static void sound(CinematicSequenceContext context, SoundEvent sound, float volume, float pitch) {
        context.player().serverLevel().playSound(
                null,
                context.anchor(),
                sound,
                SoundSource.BLOCKS,
                volume,
                pitch
        );
    }

    private static Direction facing(BlockState state) {
        return state.hasProperty(CryoTubeBlock.BlockImpl.FACING)
                ? state.getValue(CryoTubeBlock.BlockImpl.FACING)
                : Direction.NORTH;
    }

    private static CinematicStage stage(
            ResourceLocation id,
            int durationTicks,
            CinematicControlPolicy controlPolicy
    ) {
        return new CinematicStage(id, durationTicks, controlPolicy, true);
    }

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(ModConstants.MOD_ID, path);
    }

    private static ResourceLocation stageId(String path) {
        return id("cinematic/cryo_wakeup/" + path);
    }

    private static ResourceLocation actorCue(String path) {
        return id("cinematic/cryo_wakeup/actor/" + path);
    }

    private static ResourceLocation narration(String path) {
        return id("cinematic/cryo_wakeup/narration/" + path);
    }

    private static NarrationCue cueAt(int tick, ResourceLocation id) {
        return new NarrationCue(tick, id);
    }

    record NarrationCue(int tick, ResourceLocation id) {
        NarrationCue {
            if (tick < 0 || id == null) {
                throw new IllegalArgumentException("Narration cue requires a non-negative tick and id");
            }
        }
    }
}
