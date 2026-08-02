package com.thunder.wildernessodysseyapi.watersystem.water.config;

import com.thunder.wildernessodysseyapi.core.ModConstants;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

/**
 * Persists the world-wide water-authority mode independently from live gamerules.
 *
 * <p>The first server start after this data is introduced latches the configured
 * gamerule value. Later starts retain that decision so a config reload or live
 * command cannot strand namespaced fluid projections outside canonical
 * authority. Changing authority ownership therefore requires an explicit
 * migration rather than an unsafe runtime toggle.</p>
 */
public final class WaterAuthorityModeSavedData extends SavedData {

    static final int FORMAT_VERSION = 1;

    private static final String DATA_NAME = ModConstants.MOD_ID + "_water_authority_mode";
    private static final String FORMAT_KEY = "format_version";
    private static final String ENABLED_KEY = "enabled";

    private boolean enabled;

    private WaterAuthorityModeSavedData(boolean enabled, boolean markDirty) {
        this.enabled = enabled;
        if (markDirty) {
            setDirty();
        }
    }

    /** Returns the persisted mode, creating it from the current startup request once. */
    public static WaterAuthorityModeSavedData get(MinecraftServer server, boolean startupEnabled) {
        return server.overworld().getDataStorage().computeIfAbsent(
                new Factory<>(
                        () -> new WaterAuthorityModeSavedData(startupEnabled, true),
                        WaterAuthorityModeSavedData::load
                ),
                DATA_NAME
        );
    }

    /** Returns whether Wilderness authority owns water for this world. */
    public boolean enabled() {
        return enabled;
    }

    /** Persists one explicitly validated authority transition. */
    void transitionTo(boolean enabled) {
        if (this.enabled == enabled) {
            return;
        }
        this.enabled = enabled;
        setDirty();
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        tag.putInt(FORMAT_KEY, FORMAT_VERSION);
        tag.putBoolean(ENABLED_KEY, enabled);
        return tag;
    }

    // Package-private for focused codec tests without constructing a server.
    static WaterAuthorityModeSavedData load(CompoundTag tag, HolderLookup.Provider registries) {
        int format = tag.contains(FORMAT_KEY, Tag.TAG_INT) ? tag.getInt(FORMAT_KEY) : 0;
        if (format < 0 || format > FORMAT_VERSION) {
            throw new IllegalArgumentException("Unsupported water authority mode format " + format);
        }
        if (!tag.contains(ENABLED_KEY, Tag.TAG_BYTE)) {
            throw new IllegalArgumentException("Water authority mode is missing its enabled value");
        }
        return new WaterAuthorityModeSavedData(tag.getBoolean(ENABLED_KEY), false);
    }
}
