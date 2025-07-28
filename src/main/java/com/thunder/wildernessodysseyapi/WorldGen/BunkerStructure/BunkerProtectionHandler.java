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

    private static final java.util.List<AABB> bunkerBounds = new java.util.ArrayList<>();

    /**
     * Sets the bounding box of the bunker structure.
     *
     * @param bounds the structure bounds
     */
    public static void addBunkerBounds(AABB bounds) {
        bunkerBounds.add(bounds);
    }

    /**
     * Cancels block break events for protected blocks inside the bunker.
     */
    @SubscribeEvent
    public static void onBlockBreak(BlockEvent.BreakEvent event) {
        if (bunkerBounds.isEmpty()) return;

        BlockPos pos = event.getPos();
        for (AABB box : bunkerBounds) {
            if (box.contains(pos.getX(), pos.getY(), pos.getZ())) {
                BlockState state = event.getLevel().getBlockState(pos);
                if (UNBREAKABLE_BLOCKS.contains(state.getBlock())) {
                    event.setCanceled(true);
                }
                break;
            }
        }
    }
}
