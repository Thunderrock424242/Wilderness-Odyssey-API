package com.thunder.wildernessodysseyapi.ecosystem.group;

/**
 * Stable per-member variation used by the inexpensive follower goal.
 *
 * <p>The plan is derived from the entity UUID, so animals retain their loose
 * spacing and reaction character while they remain in a transient group. It
 * contains no world state and does not need to be saved.</p>
 */
public record GroupMemberPlan(
        int slotIndex,
        double radialScale,
        double angleJitter,
        double followDistanceScale,
        int reactionDelayTicks,
        int pausePeriodTicks,
        int pauseDurationTicks,
        int pausePhaseTicks
) {

    /** Returns this member's varied distance threshold around the configured baseline. */
    public double followDistance(double configuredDistance) {
        return Math.max(1.0, configuredDistance * followDistanceScale);
    }

    /** Returns whether this member may briefly graze or idle during non-emergency travel. */
    public boolean pausesAt(long gameTime) {
        return Math.floorMod(gameTime + pausePhaseTicks, pausePeriodTicks) < pauseDurationTicks;
    }
}
