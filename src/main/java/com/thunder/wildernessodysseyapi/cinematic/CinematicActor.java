package com.thunder.wildernessodysseyapi.cinematic;

import net.minecraft.resources.ResourceLocation;

/**
 * Optional world-object integration point for server-authored cinematic cues.
 *
 * <p>Implementations translate stable high-level cues into their own animation
 * state. The cinematic framework does not own GeckoLib controllers, models, or
 * block-entity rendering.</p>
 */
public interface CinematicActor {
    /**
     * Applies one sequence cue on the server.
     *
     * @return {@code true} when the actor recognized and applied the cue
     */
    boolean applyCinematicCue(ResourceLocation sequenceId, ResourceLocation cueId);
}
