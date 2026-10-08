package com.thunder.wildernessodysseyapi.meteor.worldgen;

import com.thunder.wildernessodysseyapi.util.SimplexNoise;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Real block-state generation through a region that forbids every neighboring read and write. */
class MeteorChunkPlacementTest {

    @Test
    void fragmentGeometryRemainsContinuousAcrossItsCenterChunkBoundary() throws ReflectiveOperationException {
        MeteorCraterPlan plan = new MeteorCraterPlan(new BlockPos(0, 80, 8), 125, 32, 16, 60, 91471L);
        Method fragment = MeteorFeature.class.getDeclaredMethod("placeMeteorFragment",
                MeteorPlacementContext.class, BlockPos.class, int.class, int.class,
                SimplexNoise.class, RandomSource.class);
        fragment.setAccessible(true);
        int[] tops = new int[2];
        int[] bottoms = new int[2];
        for (int index = 0; index < 2; index++) {
            int minimumX = (index - 1) * 16;
            Map<BlockPos, BlockState> blocks = new HashMap<>();
            BoundingBox clip = new BoundingBox(minimumX, -64, 0, minimumX + 15, 319, 15);
            WorldGenLevel level = (WorldGenLevel) Proxy.newProxyInstance(
                    WorldGenLevel.class.getClassLoader(), new Class<?>[]{WorldGenLevel.class},
                    (proxy, method, arguments) -> switch (method.getName()) {
                        case "getMinBuildHeight" -> -64;
                        case "getMaxBuildHeight" -> 320;
                        case "ensureCanWrite" -> clip.isInside((BlockPos) arguments[0]);
                        case "setBlock" -> {
                            BlockPos position = ((BlockPos) arguments[0]).immutable();
                            assertTrue(clip.isInside(position));
                            blocks.put(position, (BlockState) arguments[1]);
                            yield true;
                        }
                        default -> throw new AssertionError("Unexpected fragment access: " + method);
                    });
            long chunkSalt = ((long) minimumX << 32);
            fragment.invoke(new MeteorFeature(), new MeteorPlacementContext(level, plan, clip),
                    plan.center(), plan.radius(), plan.depth(), new SimplexNoise(plan.seed()),
                    RandomSource.create(plan.seed() ^ chunkSalt ^ 3L));
            assertFalse(blocks.isEmpty());
            tops[index] = blocks.keySet().stream().mapToInt(BlockPos::getY).max().orElseThrow();
            bottoms[index] = blocks.keySet().stream().mapToInt(BlockPos::getY).min().orElseThrow();
        }
        assertTrue(Math.abs(tops[0] - tops[1]) <= 1, "A central fragment must not change height at a chunk seam");
        assertEquals(bottoms[0], bottoms[1], "Both halves share one buried-root depth");
    }

    @Test
    void neighboringChunkOrderDoesNotChangeTheGeneratedBlocks() {
        Map<BlockPos, BlockState> forward = generate(0, 1);
        Map<BlockPos, BlockState> reverse = generate(1, 0);

        assertFalse(forward.isEmpty());
        assertEquals(forward, reverse);
        assertTrue(forward.keySet().stream().anyMatch(position -> position.getX() >= 16));
    }

    private static Map<BlockPos, BlockState> generate(int first, int second) {
        Map<BlockPos, BlockState> blocks = new HashMap<>();
        MeteorCraterPlan plan = new MeteorCraterPlan(new BlockPos(8, 80, 8), 125, 32, 16, 60, 91471L);
        for (int chunkX : new int[]{first, second}) {
            BoundingBox clip = new BoundingBox(chunkX * 16, -64, 0, chunkX * 16 + 15, 319, 15);
            WorldGenLevel region = (WorldGenLevel) Proxy.newProxyInstance(
                    WorldGenLevel.class.getClassLoader(), new Class<?>[]{WorldGenLevel.class},
                    (proxy, method, arguments) -> switch (method.getName()) {
                        case "getMinBuildHeight" -> -64;
                        case "getMaxBuildHeight" -> 320;
                        case "ensureCanWrite" -> clip.isInside((BlockPos) arguments[0]);
                        case "getHeight" -> {
                            int x = (int) arguments[1];
                            int z = (int) arguments[2];
                            assertTrue(clip.isInside(x, 80, z), "Height query outside supplied chunk");
                            int y = 319;
                            while (y > -64 && state(blocks, new BlockPos(x, y, z)).isAir()) y--;
                            yield y + 1;
                        }
                        case "getBlockState" -> {
                            BlockPos position = (BlockPos) arguments[0];
                            assertTrue(clip.isInside(position), "Block query outside supplied chunk");
                            yield state(blocks, position);
                        }
                        case "setBlock" -> {
                            BlockPos position = (BlockPos) arguments[0];
                            assertTrue(clip.isInside(position), "Write outside supplied chunk");
                            assertEquals(Block.UPDATE_CLIENTS, arguments[2]);
                            blocks.put(position.immutable(), (BlockState) arguments[1]);
                            yield true;
                        }
                        default -> throw new UnsupportedOperationException(method.getName());
                    });
            new MeteorFeature().placeChunk(region, plan, clip);
        }
        return blocks;
    }

    private static BlockState state(Map<BlockPos, BlockState> blocks, BlockPos position) {
        return blocks.getOrDefault(position, position.getY() <= 80
                ? Blocks.STONE.defaultBlockState() : Blocks.AIR.defaultBlockState());
    }
}
