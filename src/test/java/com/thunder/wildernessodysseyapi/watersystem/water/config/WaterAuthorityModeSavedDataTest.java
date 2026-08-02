package com.thunder.wildernessodysseyapi.watersystem.water.config;

import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Verifies the startup-latched authority decision has an independent strict format. */
class WaterAuthorityModeSavedDataTest {

    @Test
    void modeRoundTripsIndependentlyFromTheLiveGamerule() {
        CompoundTag enabledTag = new CompoundTag();
        enabledTag.putInt("format_version", WaterAuthorityModeSavedData.FORMAT_VERSION);
        enabledTag.putBoolean("enabled", true);
        assertTrue(WaterAuthorityModeSavedData.load(enabledTag, null).enabled());

        CompoundTag disabledTag = new CompoundTag();
        disabledTag.putBoolean("enabled", false);
        assertFalse(WaterAuthorityModeSavedData.load(disabledTag, null).enabled());
    }

    @Test
    void explicitTransitionChangesAndPersistsTheLatchedMode() {
        CompoundTag disabledTag = new CompoundTag();
        disabledTag.putInt("format_version", WaterAuthorityModeSavedData.FORMAT_VERSION);
        disabledTag.putBoolean("enabled", false);
        WaterAuthorityModeSavedData data = WaterAuthorityModeSavedData.load(disabledTag, null);

        data.transitionTo(true);

        assertTrue(data.enabled());
        CompoundTag saved = data.save(new CompoundTag(), null);
        assertTrue(saved.getBoolean("enabled"));
        assertTrue(data.isDirty());
    }

    @Test
    void missingModeOrFutureFormatIsRejected() {
        CompoundTag missing = new CompoundTag();
        assertThrows(IllegalArgumentException.class,
                () -> WaterAuthorityModeSavedData.load(missing, null));

        CompoundTag future = new CompoundTag();
        future.putInt("format_version", WaterAuthorityModeSavedData.FORMAT_VERSION + 1);
        future.putBoolean("enabled", true);
        assertThrows(IllegalArgumentException.class,
                () -> WaterAuthorityModeSavedData.load(future, null));
    }
}
