package com.thunder.wildernessodysseyapi.worldgen.spawn;

import com.thunder.wildernessodysseyapi.worldgen.structure.NBTStructurePlacer;
import net.minecraft.CrashReport;
import net.minecraft.ReportedException;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.world.phys.AABB;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertFalse;

/** Verifies starter placement failures stop world creation and the island retains its intended terrain. */
class SpawnBunkerPlacerTest {

    @Test
    void crashesWorldCreationWhenBunkerPlacementReturnsNoResult() {
        BlockPos anchor = new BlockPos(100, 65, -200);

        ReportedException failure = assertThrows(ReportedException.class,
                () -> SpawnBunkerPlacer.requireBunkerPlacement(anchor, () -> null));

        assertTrue(failure.getMessage().contains("Starter bunker failed to spawn"));
        assertTrue(failure.getMessage().contains("create a different world"));
        assertTrue(failure.getReport().getDetails().contains("wildernessodysseyapi:bunker"));
        assertTrue(failure.getReport().getDetails().contains("100, 65, -200"));
    }

    @Test
    void preservesPlacementExceptionInTheFatalCrashReport() {
        IllegalStateException cause = new IllegalStateException("Template placement failed");

        ReportedException failure = assertThrows(ReportedException.class,
                () -> SpawnBunkerPlacer.requireBunkerPlacement(BlockPos.ZERO, () -> {
                    throw cause;
                }));

        assertSame(cause, failure.getReport().getException());
        assertTrue(failure.getMessage().contains("create a different world"));
    }

    @Test
    void addsRecoveryGuidanceToAnExistingMinecraftPlacementCrash() {
        ReportedException cause = blockPlacementCrash();
        cause.getReport().addCategory("Original block diagnostics").setDetail("Block", "minecraft:stone");

        ReportedException failure = assertThrows(ReportedException.class,
                () -> SpawnBunkerPlacer.requireBunkerPlacement(BlockPos.ZERO, () -> {
                    throw cause;
                }));

        assertSame(cause.getReport(), failure.getReport());
        assertTrue(failure.getReport().getDetails().contains("minecraft:stone"));
        assertTrue(failure.getReport().getDetails().contains("create a different world"));
    }

    private static ReportedException blockPlacementCrash() {
        return new ReportedException(
                new CrashReport("Exception placing a block", new IllegalStateException("Block callback failed")));
    }

    @Test
    void preservesSuccessfulBunkerPlacement() {
        BlockPos anchor = new BlockPos(100, 65, -200);
        var result = new NBTStructurePlacer.PlacementResult(anchor, new AABB(anchor), List.of(anchor), List.of());

        assertSame(result, SpawnBunkerPlacer.requireBunkerPlacement(anchor, () -> result));
    }

    @Test
    void boundsGenerationToTheSelectedStarterIslandAndBuildHeight() {
        var bounds = SpawnBunkerPlacer.starterTerrainBounds(new BlockPos(-200, 10, 300), new Vec3i(157, 76, 182), -64, 320);
        assertTrue(bounds.isInside(new BlockPos(-291, -64, 222)));
        assertTrue(bounds.isInside(new BlockPos(47, 319, 560)));
        assertFalse(bounds.isInside(new BlockPos(48, 100, 560)));
        assertFalse(bounds.isInside(new BlockPos(-291, 320, 222)));
    }

    @Test
    void usesGrassAcrossTheRaisedBunkerPlatform() {
        assertTrue(SpawnBunkerPlacer.selectIslandBlock(20.0D, 60, 100, 64, 64, 63)
                .is(Blocks.GRASS_BLOCK));
    }

    @Test
    void usesDirtBelowTheGrassPlatform() {
        assertTrue(SpawnBunkerPlacer.selectIslandBlock(20.0D, 60, 100, 64, 62, 63)
                .is(Blocks.DIRT));
    }

    @Test
    void usesSandAndSandstoneOnTheSlopedShore() {
        assertTrue(SpawnBunkerPlacer.selectIslandBlock(75.0D, 60, 100, 64, 64, 63)
                .is(Blocks.SAND));
        assertTrue(SpawnBunkerPlacer.selectIslandBlock(75.0D, 60, 100, 64, 62, 63)
                .is(Blocks.SANDSTONE));
    }
}
