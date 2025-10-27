package com.thunder.wildernessodysseyapi.WorldGen.util;

import net.neoforged.fml.ModList;

import java.util.Set;

/**
 * Small helper to detect the presence of WorldEdit across the different
 * variants that exist for NeoForge. Older builds ship the classic
 * {@code worldedit} mod id while some nightlies briefly used
 * {@code worldedit_neoforge}. Rather than hard coding a single string in
 * every call site we centralise the logic here so future aliases can be added
 * in one place.
 */
public final class WorldEditCompat {

    private static final Set<String> KNOWN_MOD_IDS = Set.of(
            "worldedit",
            "worldedit_neoforge",
            "enginehub_worldedit"
    );

    private WorldEditCompat() {
    }

    /**
     * @return {@code true} if any known WorldEdit mod id is loaded.
     */
    public static boolean isInstalled() {
        ModList modList = ModList.get();
        for (String modId : KNOWN_MOD_IDS) {
            if (modList.isLoaded(modId)) {
                return true;
            }
        }
        return false;
    }
}

