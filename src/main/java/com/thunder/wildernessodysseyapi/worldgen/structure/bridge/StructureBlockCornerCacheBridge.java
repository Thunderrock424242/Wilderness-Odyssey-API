package com.thunder.wildernessodysseyapi.worldgen.structure.bridge;

/**
 * Provides hooks for structure block corner cache synchronization when lifecycle events occur on the
 * {@link net.minecraft.world.level.block.entity.StructureBlockEntity} instance.
 */
public interface StructureBlockCornerCacheBridge {

    /**
     * Ensures the structure block's corner marker registration matches the current state.
     */
    void wildernessodysseyapi$bridge$syncCornerCache();

    /**
     * Removes any registered corner markers tracked for the structure block.
     */
    void wildernessodysseyapi$bridge$removeCornerFromCache();
}
