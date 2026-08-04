package com.thunder.wildernessodysseyapi.watersystem.water.hydrology;

import com.thunder.wildernessodysseyapi.core.ModConstants;
import com.thunder.wildernessodysseyapi.watersystem.water.volume.CanonicalWater;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** World-backed regression checks for reversible temporary flood ownership. */
@GameTestHolder(ModConstants.MOD_ID)
@PrefixGameTestTemplate(false)
public final class WatershedFloodGameTests {

    private WatershedFloodGameTests() {
    }

    /** Recession restores opted-in vegetation after removing canonical floodwater. */
    @GameTest(template = "empty")
    public static void recessionRestoresOriginalReplaceableState(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos position = helper.absolutePos(new BlockPos(2, 2, 2));
        level.setBlock(position.below(), Blocks.DIRT.defaultBlockState(), 3);
        level.setBlock(position, Blocks.SHORT_GRASS.defaultBlockState(), 3);
        BlockState original = level.getBlockState(position);

        helper.assertTrue(
                CanonicalWater.placeTemporaryFlood(level, position, 0.12f, 0.0f),
                "Regression fixture could not place tracked temporary floodwater"
        );
        TemporaryFloodSavedData ledger = TemporaryFloodSavedData.get(level);
        helper.assertTrue(
                ledger.record(position, 91L, level.getGameTime(), 16, original),
                "Regression fixture could not record the original vegetation"
        );

        int removed = TemporaryFloodManager.recede(level, new WatershedSavedData(), 1);
        helper.assertTrue(
                removed == 1
                        && level.getBlockState(position).is(Blocks.SHORT_GRASS)
                        && ledger.size() == 0,
                "Flood recession did not restore the exact replaceable block state"
        );
        helper.succeed();
    }

    /** A player replacement wins over the old flood ledger claim. */
    @GameTest(template = "empty")
    public static void playerReplacementIsNeverOverwrittenByRecession(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos position = helper.absolutePos(new BlockPos(2, 2, 2));
        level.setBlock(position.below(), Blocks.DIRT.defaultBlockState(), 3);
        level.setBlock(position, Blocks.SHORT_GRASS.defaultBlockState(), 3);
        BlockState original = level.getBlockState(position);

        helper.assertTrue(
                CanonicalWater.placeTemporaryFlood(level, position, 0.0f, 0.12f),
                "Regression fixture could not place tracked temporary floodwater"
        );
        TemporaryFloodSavedData ledger = TemporaryFloodSavedData.get(level);
        helper.assertTrue(
                ledger.record(position, 92L, level.getGameTime(), 16, original),
                "Regression fixture could not record the original vegetation"
        );
        level.setBlock(position, Blocks.STONE.defaultBlockState(), 3);

        TemporaryFloodManager.recede(level, new WatershedSavedData(), 1);
        helper.assertTrue(
                level.getBlockState(position).is(Blocks.STONE) && ledger.size() == 0,
                "Flood recession overwrote a player replacement or retained a stale claim"
        );
        helper.succeed();
    }
}
