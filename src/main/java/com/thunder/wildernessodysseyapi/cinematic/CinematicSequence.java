package com.thunder.wildernessodysseyapi.cinematic;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;

import java.util.List;
import java.util.Optional;

/**
 * Reusable server-side definition for a staged scripted sequence.
 *
 * <p>The server owns timing, world cues, and gameplay restrictions. Client
 * camera and overlay implementations consume stage payloads independently, so
 * presentation never becomes gameplay authority.</p>
 */
public interface CinematicSequence {
    ResourceLocation id();

    List<CinematicStage> stages();

    /** Returns an operator-facing failure when the world cannot safely start this sequence. */
    default Optional<Component> validateStart(ServerPlayer player, BlockPos anchor) {
        return Optional.empty();
    }

    /** Finds the preferred nearby actor for a developer replay. */
    default Optional<BlockPos> findDeveloperAnchor(ServerPlayer player) {
        return Optional.of(player.blockPosition());
    }

    /** Returns whether two players must not drive the same world actor concurrently. */
    default boolean requiresExclusiveAnchor() {
        return false;
    }

    /** Returns the exact server position used while controls are locked. */
    default Vec3 lockedPosition(ServerPlayer player, BlockPos anchor) {
        return Vec3.atBottomCenterOf(anchor);
    }

    /** Returns the initial yaw sent to the client camera controller. */
    default float initialYaw(ServerPlayer player, BlockPos anchor) {
        return player.getYRot();
    }

    /** Returns the initial pitch sent to the client camera controller. */
    default float initialPitch(ServerPlayer player, BlockPos anchor) {
        return player.getXRot();
    }

    default void onStart(CinematicSequenceContext context) {
    }

    default void onStageStarted(CinematicSequenceContext context) {
    }

    default void onTick(CinematicSequenceContext context) {
    }

    default void onStop(CinematicSequenceContext context, CinematicStopReason reason) {
    }
}
