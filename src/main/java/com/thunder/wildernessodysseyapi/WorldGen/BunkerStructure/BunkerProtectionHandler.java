package com.thunder.wildernessodysseyapi.WorldGen.BunkerStructure;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.level.BlockEvent;

import java.util.Set;

import static com.thunder.wildernessodysseyapi.Core.ModConstants.MOD_ID;

/**
 * Prevents breaking certain blocks within the bunker bounds.
 */
@EventBusSubscriber(modid = MOD_ID)
public class BunkerProtectionHandler {

    private static final Set<Block> UNBREAKABLE_BLOCKS = Set.of(
            Blocks.OBSIDIAN,
            Blocks.BEDROCK
    );

    private static AABB bunkerBounds;

    /**
     * Sets the bounding box of the bunker structure.
     *
     * @param bounds the structure bounds
     */
    public static void setBunkerBounds(AABB bounds) {
        bunkerBounds = bounds;
    }

    /**
     * Cancels block break events for protected blocks inside the bunker.
     */
    @SubscribeEvent
    public static void onBlockBreak(BlockEvent.BreakEvent event) {
        if (bunkerBounds == null) return;

        BlockPos pos = event.getPos();
        if (!bunkerBounds.contains(pos.getX(), pos.getY(), pos.getZ())) return;

        BlockState state = event.getLevel().getBlockState(pos);
        if (UNBREAKABLE_BLOCKS.contains(state.getBlock())) {
            event.setCanceled(true);
        }
    }
}
