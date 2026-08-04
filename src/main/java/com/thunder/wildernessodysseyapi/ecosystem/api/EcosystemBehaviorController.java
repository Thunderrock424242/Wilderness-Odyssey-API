package com.thunder.wildernessodysseyapi.ecosystem.api;

import com.thunder.wildernessodysseyapi.ecosystem.state.AnimalNeedsState;

/**
 * Selects one high-level ecosystem action from a bounded environmental snapshot.
 *
 * <p>The runtime goal owns navigation and vanilla-AI coordination; controller
 * implementations only choose and initialize ecosystem state.</p>
 */
@FunctionalInterface
public interface EcosystemBehaviorController {

    /** Returns whether the supplied context selected an active ecosystem behavior. */
    boolean evaluate(EnvironmentalContext context, AnimalNeedsState needs);
}
