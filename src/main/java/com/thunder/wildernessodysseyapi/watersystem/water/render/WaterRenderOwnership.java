package com.thunder.wildernessodysseyapi.watersystem.water.render;

/** One transition policy for config switches, shader ownership and level teardown. */
final class WaterRenderOwnership {
    enum Owner { FALLBACK, NATIVE, EXTERNAL }
    private Owner owner = Owner.FALLBACK;

    Transition update(Owner next) {
        if (next == owner) return Transition.NONE;
        owner = next;
        return new Transition(true, next == Owner.NATIVE, true);
    }

    void reset() { owner = Owner.FALLBACK; }

    record Transition(boolean rebuildBaked, boolean rebuildMeshes, boolean releaseResources) {
        static final Transition NONE = new Transition(false, false, false);
    }
}
