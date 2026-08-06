package com.thunder.wildernessodysseyapi.watersystem.water.render;

/**
 * Duck interface added to vanilla and optional-renderer section build outputs.
 *
 * <p>Keeping the receipt on the exact build object avoids mistaking a stale or
 * cancelled compilation for the generation that removed fallback water tops.</p>
 */
public interface WaterHandoffBuildOutput {

    /** Stores the suppression receipt produced by this exact compilation. */
    void wildernessOdysseyApi$setWaterHandoffReceipt(WaterHandoffReceipt receipt);

    /** Returns the suppression receipt carried through to renderer upload. */
    WaterHandoffReceipt wildernessOdysseyApi$getWaterHandoffReceipt();
}
