package com.thunder.wildernessodysseyapi.cinematic;

import net.minecraft.resources.ResourceLocation;

import java.util.Objects;

/**
 * One stable, network-addressable interval in a cinematic timeline.
 *
 * @param id stable stage id shared with client presentation code
 * @param durationTicks authoritative server duration in game ticks
 * @param controlPolicy player-control policy for this interval
 * @param hideHud whether the ordinary HUD should be hidden during this interval
 */
public record CinematicStage(
        ResourceLocation id,
        int durationTicks,
        CinematicControlPolicy controlPolicy,
        boolean hideHud
) {
    public CinematicStage {
        id = Objects.requireNonNull(id, "id");
        controlPolicy = Objects.requireNonNull(controlPolicy, "controlPolicy");
        if (durationTicks <= 0) {
            throw new IllegalArgumentException("Cinematic stage duration must be positive");
        }
    }

    /** Returns whether this stage requires authoritative movement and interaction safety. */
    public boolean locksControls() {
        return controlPolicy == CinematicControlPolicy.LOCKED;
    }
}
