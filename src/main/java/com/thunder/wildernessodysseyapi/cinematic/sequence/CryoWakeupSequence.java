package com.thunder.wildernessodysseyapi.cinematic.sequence;

import com.thunder.wildernessodysseyapi.cinematic.CinematicControlPolicy;
import com.thunder.wildernessodysseyapi.cinematic.CinematicSequence;
import com.thunder.wildernessodysseyapi.cinematic.CinematicSequenceContext;
import com.thunder.wildernessodysseyapi.cinematic.CinematicStage;
import com.thunder.wildernessodysseyapi.cinematic.CinematicStopReason;
import com.thunder.wildernessodysseyapi.core.ModConstants;
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
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import java.util.List;
import java.util.Optional;

/**
 * Twenty-four-second cryogenic wake-up built on the generic cinematic timeline.
 *
 * <p>All cue times are expressed here as stage durations. World effects occur
 * once at boundaries, while camera, eyelids, and warning text remain entirely
 * client-presentational.</p>
 */
public final class CryoWakeupSequence implements CinematicSequence {
    public static final ResourceLocation ID = id("cryo_wakeup");

    public static final ResourceLocation BLACK_SCREEN = stageId("black_screen");
    public static final ResourceLocation MACHINERY_HUM = stageId("machinery_hum");
    public static final ResourceLocation HEARTBEAT = stageId("heartbeat");
    public static final ResourceLocation EYES_PARTIAL = stageId("eyes_partial");
    public static final ResourceLocation EYES_CLOSED = stageId("eyes_closed");
    public static final ResourceLocation EYES_REOPENING = stageId("eyes_reopening");
    public static final ResourceLocation LIGHTS_FLICKER = stageId("lights_flicker");
    public static final ResourceLocation WARNING_STARTED = stageId("warning_started");
    public static final ResourceLocation WARNING_LIGHTS = stageId("warning_lights");
    public static final ResourceLocation ALARM_BEEPS = stageId("alarm_beeps");
    public static final ResourceLocation RELEASE_STARTED = stageId("release_started");
    public static final ResourceLocation LOCKS_DISENGAGED = stageId("locks_disengaged");
    public static final ResourceLocation MIST_RELEASE = stageId("mist_release");
    public static final ResourceLocation CRYO_OPENING = stageId("cryo_opening");
    public static final ResourceLocation CAMERA_TURN = stageId("camera_turn");
    public static final ResourceLocation CRYO_OPEN = stageId("cryo_open");
    public static final ResourceLocation LIGHTS_STABLE = stageId("lights_stable");
    public static final ResourceLocation CAMERA_RELEASE = stageId("camera_release");
    public static final ResourceLocation CONTROL_RETURN = stageId("control_return");

    public static final ResourceLocation CUE_IDLE = cueId("idle");
    public static final ResourceLocation CUE_WARNING = cueId("warning");
    public static final ResourceLocation CUE_UNLOCK = cueId("unlock");
    public static final ResourceLocation CUE_OPENING = cueId("opening");
    public static final ResourceLocation CUE_OPEN = cueId("open");

    private static final List<CinematicStage> STAGES = List.of(
            stage(BLACK_SCREEN, 20, CinematicControlPolicy.LOCKED),       // 0:00
            stage(MACHINERY_HUM, 20, CinematicControlPolicy.LOCKED),      // 0:01
            stage(HEARTBEAT, 20, CinematicControlPolicy.LOCKED),          // 0:02
            stage(EYES_PARTIAL, 40, CinematicControlPolicy.LOCKED),       // 0:03
            stage(EYES_CLOSED, 20, CinematicControlPolicy.LOCKED),        // 0:05
            stage(EYES_REOPENING, 20, CinematicControlPolicy.LOCKED),     // 0:06
            stage(LIGHTS_FLICKER, 20, CinematicControlPolicy.LOCKED),     // 0:07
            stage(WARNING_STARTED, 20, CinematicControlPolicy.LOCKED),    // 0:08
            stage(WARNING_LIGHTS, 20, CinematicControlPolicy.LOCKED),     // 0:09
            stage(ALARM_BEEPS, 40, CinematicControlPolicy.LOCKED),        // 0:10
            stage(RELEASE_STARTED, 20, CinematicControlPolicy.LOCKED),    // 0:12
            stage(LOCKS_DISENGAGED, 20, CinematicControlPolicy.LOCKED),   // 0:13
            stage(MIST_RELEASE, 20, CinematicControlPolicy.LOCKED),       // 0:14
            stage(CRYO_OPENING, 40, CinematicControlPolicy.LOCKED),       // 0:15
            stage(CAMERA_TURN, 40, CinematicControlPolicy.LOCKED),        // 0:17
            stage(CRYO_OPEN, 20, CinematicControlPolicy.LOCKED),          // 0:19
            stage(LIGHTS_STABLE, 40, CinematicControlPolicy.LOCKED),      // 0:20
            stage(CAMERA_RELEASE, 20, CinematicControlPolicy.LOCKED),     // 0:22
            stage(CONTROL_RETURN, 20, CinematicControlPolicy.PRESENTATION_ONLY) // 0:23
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
    public Optional<Component> validateStart(ServerPlayer player, BlockPos anchor) {
        ServerLevel level = player.serverLevel();
        if (!level.hasChunkAt(anchor)) {
            return Optional.of(Component.literal("The cryo tube chunk is not loaded."));
        }
        if (!level.getBlockState(anchor).is(CryoTubeBlock.CRYO_TUBE.get())) {
            return Optional.of(Component.literal("No cryo tube exists at the cinematic anchor."));
        }
        BlockEntity blockEntity = level.getBlockEntity(anchor);
        if (!(blockEntity instanceof com.thunder.wildernessodysseyapi.cinematic.CinematicActor)) {
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
        return new Vec3(anchor.getX() + 0.5D, anchor.getY() + 0.5D, anchor.getZ() + 0.5D);
    }

    @Override
    public float initialYaw(ServerPlayer player, BlockPos anchor) {
        BlockState state = player.serverLevel().getBlockState(anchor);
        Direction facing = state.hasProperty(CryoTubeBlock.BlockImpl.FACING)
                ? state.getValue(CryoTubeBlock.BlockImpl.FACING)
                : Direction.NORTH;
        return facing.toYRot();
    }

    @Override
    public float initialPitch(ServerPlayer player, BlockPos anchor) {
        return -4.0F;
    }

    @Override
    public void onStart(CinematicSequenceContext context) {
        context.cueActor(CUE_IDLE);
    }

    @Override
    public void onStageStarted(CinematicSequenceContext context) {
        ResourceLocation stage = context.stage().id();
        if (stage.equals(MACHINERY_HUM)) {
            sound(context, SoundEvents.BEACON_AMBIENT, 0.32F, 0.62F);
        } else if (stage.equals(HEARTBEAT)) {
            sound(context, SoundEvents.PLAYER_BIG_FALL, 0.24F, 0.55F);
            sound(context, SoundEvents.PLAYER_BREATH, 0.34F, 0.72F);
        } else if (stage.equals(LIGHTS_FLICKER)) {
            sound(context, SoundEvents.REDSTONE_TORCH_BURNOUT, 0.38F, 0.82F);
        } else if (stage.equals(WARNING_STARTED)) {
            context.cueActor(CUE_WARNING);
            sound(context, SoundEvents.BEACON_POWER_SELECT, 0.42F, 1.45F);
        } else if (stage.equals(ALARM_BEEPS)) {
            sound(context, SoundEvents.BEACON_DEACTIVATE, 0.48F, 1.62F);
        } else if (stage.equals(RELEASE_STARTED)) {
            sound(context, SoundEvents.FIRE_EXTINGUISH, 0.58F, 0.74F);
        } else if (stage.equals(LOCKS_DISENGAGED)) {
            context.cueActor(CUE_UNLOCK);
            sound(context, SoundEvents.IRON_DOOR_OPEN, 0.52F, 0.68F);
        } else if (stage.equals(MIST_RELEASE)) {
            spawnMist(context);
            sound(context, SoundEvents.FIRE_EXTINGUISH, 0.72F, 0.62F);
        } else if (stage.equals(CRYO_OPENING)) {
            context.cueActor(CUE_OPENING);
            sound(context, SoundEvents.PISTON_EXTEND, 0.58F, 0.72F);
        } else if (stage.equals(CRYO_OPEN)) {
            context.cueActor(CUE_OPEN);
            sound(context, SoundEvents.IRON_DOOR_OPEN, 0.50F, 0.84F);
        }
    }

    @Override
    public void onTick(CinematicSequenceContext context) {
        if (context.stage().id().equals(ALARM_BEEPS)
                && context.stageElapsedTicks() > 0
                && context.stageElapsedTicks() % 10 == 0) {
            sound(context, SoundEvents.BEACON_DEACTIVATE, 0.42F, 1.68F);
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

    /** Returns the complete authoritative timeline duration. */
    public static int totalDurationTicks() {
        return STAGES.stream().mapToInt(CinematicStage::durationTicks).sum();
    }

    private static void spawnMist(CinematicSequenceContext context) {
        BlockPos anchor = context.anchor();
        context.player().serverLevel().sendParticles(
                ParticleTypes.CLOUD,
                anchor.getX() + 0.5D,
                anchor.getY() + 1.0D,
                anchor.getZ() + 0.5D,
                18,
                0.38D,
                0.72D,
                0.38D,
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

    private static ResourceLocation cueId(String path) {
        return id("cinematic/cryo_wakeup/cue/" + path);
    }
}
