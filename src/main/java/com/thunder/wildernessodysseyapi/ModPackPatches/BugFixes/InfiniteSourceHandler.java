package com.thunder.wildernessodysseyapi.BugFixes;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BiomeTags;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.level.BlockEvent;

/**
 * The type Infinite source handler.
 */
public class InfiniteSourceHandler {

    /**
     * On water or lava update.
     *
     * @param event the event
     */
    @SubscribeEvent
    public void onWaterOrLavaUpdate(BlockEvent.NeighborNotifyEvent event) {
        // Ensure this is a server-side event
        if (!(event.getLevel() instanceof ServerLevel serverLevel)) return;

        BlockState state = event.getState();
        BlockPos pos = event.getPos();

        // Handle water source blocks
        if (state.is(Blocks.WATER) && state.getFluidState().isSource()) {
            // Check if the biome is an ocean using IS_OCEAN tag
            if (!serverLevel.getBiome(pos).is(BiomeTags.IS_OCEAN)) {
                // Prevent forming infinite water source
                serverLevel.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
            }
        }

        // Handle lava source blocks
        if (state.is(Blocks.LAVA) && state.getFluidState().isSource()) {
            // Disable infinite lava sources universally
            serverLevel.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
        }
    }
}