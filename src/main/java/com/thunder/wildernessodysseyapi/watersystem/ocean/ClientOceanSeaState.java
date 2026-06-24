package com.thunder.wildernessodysseyapi.watersystem.ocean;

import net.minecraft.world.level.Level;

import java.util.IdentityHashMap;
import java.util.Map;

/**
 * Holds interpolated server sea-state snapshots for client rendering.
 *
 * <p>This class deliberately depends only on common Minecraft types so payload
 * registration remains safe on a dedicated server. Calls are made on the
 * client main thread by NeoForge packet and tick handlers.</p>
 */
public final class ClientOceanSeaState {

    private static final float SNAPSHOT_INTERPOLATION = 0.12f;
    private static final Map<Level, State> STATES = new IdentityHashMap<>();

    private ClientOceanSeaState() {
    }

    /** Accepts a new server-authoritative target for the active client level. */
    public static void accept(Level level, OceanSeaState.Sample sample) {
        State state = STATES.computeIfAbsent(level, ignored -> new State(sample, sample));
        state.target = sample;
    }

    /** Smoothly approaches the latest snapshot once per client tick. */
    public static void tick(Level level) {
        State state = STATES.computeIfAbsent(level, ignored -> {
            OceanSeaState.Sample fallback = OceanSeaState.sample(level, 0.0f);
            return new State(fallback, fallback);
        });
        state.current = state.current.interpolate(state.target, SNAPSHOT_INTERPOLATION);
    }

    /** Returns the interpolated state, falling back to synchronized vanilla weather. */
    public static OceanSeaState.Sample current(Level level) {
        State state = STATES.get(level);
        return state == null ? OceanSeaState.sample(level, 0.0f) : state.current;
    }

    /** Releases client-world identity state during disconnect or dimension unload. */
    public static void clear(Level level) {
        STATES.remove(level);
    }

    private static final class State {
        private OceanSeaState.Sample current;
        private OceanSeaState.Sample target;

        private State(OceanSeaState.Sample current, OceanSeaState.Sample target) {
            this.current = current;
            this.target = target;
        }
    }
}
