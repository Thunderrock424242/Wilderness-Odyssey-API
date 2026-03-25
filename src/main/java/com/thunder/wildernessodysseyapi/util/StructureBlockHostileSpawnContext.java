package com.thunder.wildernessodysseyapi.util;

public final class StructureBlockHostileSpawnContext {
    private static final ThreadLocal<Boolean> DISABLE_HOSTILE_SPAWNS = ThreadLocal.withInitial(() -> Boolean.FALSE);

    private StructureBlockHostileSpawnContext() {
    }

    public static void setDisableHostileSpawns(boolean disabled) {
        DISABLE_HOSTILE_SPAWNS.set(disabled);
    }

    public static boolean isDisableHostileSpawns() {
        return DISABLE_HOSTILE_SPAWNS.get();
    }

    public static void clear() {
        DISABLE_HOSTILE_SPAWNS.remove();
    }
}
