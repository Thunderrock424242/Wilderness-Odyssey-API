package com.thunder.wildernessodysseyapi.meteor.worldgen;

import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

/** Exercises a feature's refusal to partly modify an insufficient generation region. */
class MeteorFeatureBoundaryTest {

    @Test
    void rejectsInsufficientGenerationRegionBeforeChangingBlocks() {
        AtomicInteger writes = new AtomicInteger();
        WorldGenLevel region = (WorldGenLevel) Proxy.newProxyInstance(
                WorldGenLevel.class.getClassLoader(), new Class<?>[]{WorldGenLevel.class},
                (proxy, method, arguments) -> switch (method.getName()) {
                    case "getMinBuildHeight" -> -64;
                    case "getMaxBuildHeight" -> 320;
                    case "getHeight" -> 384;
                    case "ensureCanWrite" -> inside((BlockPos) arguments[0]);
                    case "getBlockState" -> {
                        BlockPos position = (BlockPos) arguments[0];
                        if (!inside(position)) {
                            throw new IllegalStateException("Unavailable world-generation neighbor");
                        }
                        yield position.getY() <= 64
                                ? Blocks.STONE.defaultBlockState() : Blocks.AIR.defaultBlockState();
                    }
                    case "setBlock" -> {
                        writes.incrementAndGet();
                        yield inside((BlockPos) arguments[0]);
                    }
                    case "getSeed" -> 1234L;
                    default -> throw new UnsupportedOperationException(method.getName());
                });
        FeaturePlaceContext<NoneFeatureConfiguration> context = new FeaturePlaceContext<>(
                Optional.empty(), region, null, RandomSource.create(1234L),
                new BlockPos(8, 64, 8), NoneFeatureConfiguration.INSTANCE);

        boolean placed = assertDoesNotThrow(() -> new MeteorFeature().place(context),
                "An unavailable neighbor must be rejected before crater generation begins");

        assertFalse(placed);
        assertEquals(0, writes.get(), "A refused crater must leave no partial terrain edits");
    }

    private static boolean inside(BlockPos position) {
        return Math.abs(position.getX() >> 4) <= 1
                && Math.abs(position.getZ() >> 4) <= 1
                && position.getY() >= -64 && position.getY() < 320;
    }
}
