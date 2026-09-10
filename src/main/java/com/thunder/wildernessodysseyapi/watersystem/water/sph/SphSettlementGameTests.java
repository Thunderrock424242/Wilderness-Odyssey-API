package com.thunder.wildernessodysseyapi.watersystem.water.sph;

import com.thunder.wildernessodysseyapi.core.ModConstants;
import com.thunder.wildernessodysseyapi.watersystem.water.volume.CanonicalWater;
import com.thunder.wildernessodysseyapi.watersystem.water.volume.WaterVolumeChunk;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.List;
import java.util.UUID;

/** Exercises real canonical settlement capacity and rollback, not particle-count arithmetic. */
@GameTestHolder(ModConstants.MOD_ID)
@PrefixGameTestTemplate(false)
public final class SphSettlementGameTests {
    private SphSettlementGameTests() { }

    @GameTest(template = "empty")
    public static void failedPartialSettlementRollsBackExactUnits(GameTestHelper helper) {
        check(helper, 8192, false);
    }

    @GameTest(template = "empty")
    public static void completeSettlementCreditsExactlyRepresentedUnits(GameTestHelper helper) {
        check(helper, 4096, true);
    }

    private static void check(GameTestHelper helper, int units, boolean expectedSuccess) {
        var level = helper.getLevel();
        BlockPos requested = helper.absolutePos(new BlockPos(4, 2, 4));
        var particle = new SPHParticle(requested.getX() + .5f, requested.getY() + .5f, requested.getZ() + .5f);
        // GameTest can select far-world coordinates where float sub-block
        // positions round to the next voxel. Enclose the actual solver anchor.
        BlockPos center = BlockPos.containing(particle.position.x, particle.position.y, particle.position.z);
        for (BlockPos pos : BlockPos.betweenClosed(center.offset(-3, 0, -3), center.offset(3, 4, 3))) {
            CanonicalWater.set(level, pos, WaterVolumeChunk.WaterCell.EMPTY, false, false);
            level.setBlock(pos, Blocks.STONE.defaultBlockState(), 3);
        }
        level.setBlock(center, Blocks.AIR.defaultBlockState(), 3);
        var particles = List.of(particle);
        var body = SPHSimulator.restoreAuthoritative(UUID.randomUUID(), level, particles, units);
        boolean accepted = SPHSimulationManager.materializeCanonicalVolume(level, body, particles, false);
        helper.assertTrue(accepted == expectedSuccess, "Settlement success did not match exact available capacity");
        helper.assertTrue(CanonicalWater.get(level, center).volumeUnits() == (accepted ? units : 0),
                "Settlement left a partial canonical credit or lost exact volume");
        if (!accepted) helper.assertTrue(body.getCanonicalVolumeUnits() == units, "Rollback changed SPH-owned volume");
        helper.succeed();
    }
}
