package com.thunder.wildernessodysseyapi.cinematic;

import com.thunder.wildernessodysseyapi.cinematic.sequence.CryoWakeupSequence;

/** Built-in scripted sequences registered by Wilderness Odyssey. */
public final class CinematicSequences {
    public static final CryoWakeupSequence CRYO_WAKEUP = new CryoWakeupSequence();

    private static boolean bootstrapped;

    private CinematicSequences() {
    }

    /** Registers built-in definitions once during common mod construction. */
    public static synchronized void bootstrap() {
        if (bootstrapped) {
            return;
        }
        CinematicSequenceRegistry.register(CRYO_WAKEUP);
        bootstrapped = true;
    }
}
