package com.thunder.wildernessodysseyapi.cinematic;

import net.minecraft.core.BlockPos;

import java.util.Objects;

/**
 * Server-owned options for one playback request.
 *
 * @param anchor exact world actor/position associated with the sequence
 * @param markCompletion whether normal completion should update permanent story progress
 */
public record CinematicPlaybackOptions(BlockPos anchor, boolean markCompletion) {
    public CinematicPlaybackOptions {
        anchor = Objects.requireNonNull(anchor, "anchor").immutable();
    }

    /** Creates normal first-time story playback options. */
    public static CinematicPlaybackOptions automatic(BlockPos anchor) {
        return new CinematicPlaybackOptions(anchor, true);
    }

    /** Creates developer replay options that never mutate permanent completion state. */
    public static CinematicPlaybackOptions developerReplay(BlockPos anchor) {
        return new CinematicPlaybackOptions(anchor, false);
    }
}
