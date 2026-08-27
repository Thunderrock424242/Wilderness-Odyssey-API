package com.thunder.wildernessodysseyapi.cinematic;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

/** Read-only server context exposed to sequence callbacks for one active player. */
public interface CinematicSequenceContext {
    ServerPlayer player();

    BlockPos anchor();

    CinematicStage stage();

    int stageElapsedTicks();

    boolean marksCompletion();

    /** Sends a high-level cue to the block entity cached by position for this sequence. */
    boolean cueActor(ResourceLocation cueId);

    /** Sends a registered client narration cue once during this playback session. */
    boolean narrateOnce(ResourceLocation cueId, int durationTicks);
}
