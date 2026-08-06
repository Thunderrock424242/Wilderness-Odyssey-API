package com.thunder.wildernessodysseyapi.watersystem.water.render;

/**
 * Identifies one renderer upload that compiled a complete suppression mask.
 *
 * <p>The generation prevents an older chunk rebuild from publishing custom
 * ownership after a newer snapshot has replaced it.</p>
 */
public record WaterHandoffReceipt(long chunkKey, long sectionKey, long generation) {

    /** Sentinel used when a compilation did not cover a pending water section. */
    public static final WaterHandoffReceipt NONE = new WaterHandoffReceipt(0L, 0L, 0L);

    /** Returns whether this receipt can acknowledge a pending handoff. */
    public boolean valid() {
        return generation > 0L;
    }
}
