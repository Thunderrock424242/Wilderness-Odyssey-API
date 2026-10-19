package com.thunder.wildernessodysseyapi.watersystem.water.render;

/** One transition policy for config switches, shader ownership and level teardown. */
final class WaterRenderOwnership {
    enum Owner { FALLBACK, NATIVE, EXTERNAL }
    private Owner owner = Owner.FALLBACK;
    private boolean smoothRiverCurrents;

    Transition update(Owner next) {
        return update(next, smoothRiverCurrents);
    }

    Transition update(Owner next, boolean nextSmoothRiverCurrents) {
        boolean ownerChanged = next != owner;
        boolean currentSettingsChanged = smoothRiverCurrents != nextSmoothRiverCurrents;
        owner = next;
        smoothRiverCurrents = nextSmoothRiverCurrents;
        return new Transition(ownerChanged,
                (ownerChanged || currentSettingsChanged) && next == Owner.NATIVE, ownerChanged);
    }

    void reset() { owner = Owner.FALLBACK; smoothRiverCurrents = false; }

    record Transition(boolean rebuildBaked, boolean rebuildMeshes, boolean releaseResources) {
        static final Transition NONE = new Transition(false, false, false);
    }
}
