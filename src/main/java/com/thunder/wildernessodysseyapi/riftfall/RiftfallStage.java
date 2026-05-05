package com.thunder.wildernessodysseyapi.riftfall;

public enum RiftfallStage {
    CLEAR,
    WARNING,
    ACTIVE,
    METEOR_SURGE,
    ENDING;

    public boolean isActiveDanger() {
        return this == ACTIVE || this == METEOR_SURGE;
    }
}
