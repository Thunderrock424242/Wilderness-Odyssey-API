package com.thunder.wildernessodysseyapi.ecosystem.behavior;

/** Pure safeguard gate that prevents continuous or population-destructive ecosystem hunts. */
public final class PredatorHuntingPolicy {

    private PredatorHuntingPolicy() {
    }

    /** Returns whether the predator may begin one bounded hunt. */
    public static boolean mayHunt(
            boolean enabled,
            boolean wildOrAllowed,
            boolean alreadyHasTarget,
            double hunger,
            double hungerThreshold,
            long gameTime,
            long nextHuntAllowedAt,
            int adultPreyPopulation,
            int minimumNearbyPrey
    ) {
        return enabled
                && wildOrAllowed
                && !alreadyHasTarget
                && hunger >= hungerThreshold
                && gameTime >= nextHuntAllowedAt
                && adultPreyPopulation >= minimumNearbyPrey;
    }
}
