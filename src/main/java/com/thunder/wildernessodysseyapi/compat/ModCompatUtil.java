package com.thunder.wildernessodysseyapi.compat;

import net.neoforged.fml.ModList;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Utility helpers for optional mod compatibility.
 * <p>
 * Provides convenience methods for checking if other mods are loaded and
 * conditionally performing actions such as block replacement.
 */
public final class ModCompatUtil {
    private ModCompatUtil() {
    }

    /**
     * Checks whether the supplied mod id is currently loaded.
     *
     * @param modId the mod identifier to check
     * @return {@code true} if the mod is present, {@code false} otherwise
     */
    public static boolean isModLoaded(String modId) {
        return ModList.get().isLoaded(modId);
    }

    /**
     * Replaces a block at the given position if the specified mod is loaded.
     *
     * @param level the world the block resides in
     * @param pos   the block position to replace
     *     @param state the new block state to place
     * @param modId the mod id that gatekeeps the replacement
     * @return {@code true} if the block was replaced
     */
    public static boolean replaceBlockIfModPresent(Level level, BlockPos pos, BlockState state, String modId) {
        if (!isModLoaded(modId)) {
            return false;
        }
        return level.setBlock(pos, state, 3);
    }
}
