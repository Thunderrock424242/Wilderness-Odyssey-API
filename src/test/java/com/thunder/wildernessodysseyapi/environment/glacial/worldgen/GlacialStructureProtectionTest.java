package com.thunder.wildernessodysseyapi.environment.glacial.worldgen;

import com.thunder.wildernessodysseyapi.environment.glacial.config.GlacialConfig;
import com.thunder.wildernessodysseyapi.worldgen.biome.ModBiomes;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderOwner;
import net.minecraft.core.HolderSet;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ProtoChunk;
import net.minecraft.world.level.chunk.UpgradeData;
import net.minecraft.world.level.levelgen.GenerationStep;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.level.levelgen.structure.TerrainAdjustment;
import net.minecraft.world.level.levelgen.structure.pieces.PiecesContainer;
import net.minecraft.world.level.levelgen.structure.structures.BuriedTreasurePieces;
import net.minecraft.world.level.levelgen.structure.structures.BuriedTreasureStructure;
import net.neoforged.neoforge.common.ModConfigSpec;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Proxy;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Uses real chunk structure metadata and a bounded in-memory worldgen accessor. */
class GlacialStructureProtectionTest {

    private final Map<ModConfigSpec.BooleanValue, Object> previousConfigValues = new HashMap<>();

    @BeforeEach
    void enableFeaturesWithoutLoadingOrSavingAConfigFile() throws ReflectiveOperationException {
        Field cachedValue = ModConfigSpec.ConfigValue.class.getDeclaredField("cachedValue");
        cachedValue.setAccessible(true);
        for (ModConfigSpec.BooleanValue value : List.of(
                GlacialConfig.ENABLE_POLAR_BIOME_SYSTEM,
                GlacialConfig.ENABLE_GLACIER_CREVASSES,
                GlacialConfig.ENABLE_GLACIAL_RIVERS)) {
            previousConfigValues.put(value, cachedValue.get(value));
            cachedValue.set(value, true);
        }
    }

    @AfterEach
    void restoreConfigCache() throws ReflectiveOperationException {
        Field cachedValue = ModConfigSpec.ConfigValue.class.getDeclaredField("cachedValue");
        cachedValue.setAccessible(true);
        for (var entry : previousConfigValues.entrySet()) {
            cachedValue.set(entry.getKey(), entry.getValue());
        }
    }

    @Test
    void protectsAReferencedStartWithoutRequestingItsDistantChunk() {
        WorldFixture fixture = new WorldFixture();
        fixture.chunk.setAllReferences(Map.of(fixture.structure,
                new LongOpenHashSet(new long[]{ChunkPos.asLong(9, 0)})));

        assertTrue(GlacialFeatureSupport.structureGuard(fixture.level)
                .contains(new BlockPos(8, 64, 8)));
    }

    @Test
    void crevasseWallAccentsDoNotReplaceStructureBlocksBesideAnUnprotectedCut() {
        WorldFixture fixture = new WorldFixture();
        BoundingBox protectedWall = new BoundingBox(0, 0, 0, 0, 64, 15);
        fixture.addStart(protectedWall);

        assertTrue(new GlacialCrevasseFeature().place(fixture.context(new BlockPos(1, 64, 8))));
        assertFalse(fixture.writes.keySet().stream().anyMatch(protectedWall::isInside));
    }

    @Test
    void crevasseBottomWaterDoesNotBypassAProtectedColumn() {
        WorldFixture fixture = new WorldFixture();
        BoundingBox protectedFloor = new BoundingBox(0, 0, 0, 15, 63, 15);
        fixture.addStart(protectedFloor);

        assertTrue(new GlacialCrevasseFeature().place(fixture.context(new BlockPos(8, 64, 8))));
        assertFalse(fixture.writes.keySet().stream().anyMatch(protectedFloor::isInside));
    }

    @Test
    void riverBedAndWaterDoNotReplaceAStructureBelowAnUnprotectedSurface() {
        WorldFixture fixture = new WorldFixture();
        BoundingBox protectedFloor = new BoundingBox(0, 0, 0, 15, 63, 15);
        fixture.addStart(protectedFloor);

        new GlacialRiverFeature().place(fixture.context(new BlockPos(8, 64, 8)));

        assertFalse(fixture.writes.keySet().stream().anyMatch(protectedFloor::isInside));
    }

    private static final class WorldFixture {
        // Structure metadata does not depend on block sections; avoid a biome
        // registry and the loaded-server lifecycle for this focused fixture.
        private final ProtoChunk chunk = new ProtoChunk(
                new ChunkPos(0, 0), UpgradeData.EMPTY, LevelHeightAccessor.create(0, 0), null, null);
        private final Structure structure = new BuriedTreasureStructure(new Structure.StructureSettings(
                HolderSet.direct(List.of()), Map.of(), GenerationStep.Decoration.SURFACE_STRUCTURES,
                TerrainAdjustment.NONE));
        private final Map<BlockPos, BlockState> writes = new HashMap<>();
        private final WorldGenLevel level = (WorldGenLevel) Proxy.newProxyInstance(
                WorldGenLevel.class.getClassLoader(), new Class<?>[]{WorldGenLevel.class},
                (proxy, method, arguments) -> switch (method.getName()) {
                    case "getMinBuildHeight" -> 0;
                    case "getMaxBuildHeight", "getHeight" -> arguments == null ? 256 : 65;
                    case "ensureCanWrite" -> inChunk((BlockPos) arguments[0]);
                    case "getChunk" -> {
                        BlockPos position = (BlockPos) arguments[0];
                        if (!inChunk(position)) {
                            throw new AssertionError("Must not query a neighboring start chunk: " + position);
                        }
                        yield chunk;
                    }
                    case "getBlockState" -> writes.getOrDefault(arguments[0], Blocks.PACKED_ICE.defaultBlockState());
                    case "getBiome" -> Holder.Reference.createStandAlone(
                            new HolderOwner<>() {}, ModBiomes.POLAR_GLACIAL_BASIN_KEY);
                    case "setBlock" -> {
                        BlockPos position = ((BlockPos) arguments[0]).immutable();
                        if (!inChunk(position)) {
                            throw new AssertionError("Must not write outside the owning chunk: " + position);
                        }
                        writes.put(position, (BlockState) arguments[1]);
                        yield true;
                    }
                    default -> throw new AssertionError("Unexpected worldgen access: " + method);
                });

        private void addStart(BoundingBox box) {
            chunk.setAllStarts(Map.of(structure, new StructureStart(
                    structure, chunk.getPos(), 0, new PiecesContainer(List.of(new BoxPiece(box))))));
        }

        private FeaturePlaceContext<NoneFeatureConfiguration> context(BlockPos origin) {
            return new FeaturePlaceContext<>(Optional.empty(), level, null, RandomSource.create(13L),
                    origin, NoneFeatureConfiguration.INSTANCE);
        }

        private static boolean inChunk(BlockPos position) {
            return position.getX() >= 0 && position.getX() < 16
                    && position.getZ() >= 0 && position.getZ() < 16;
        }
    }

    private static final class BoxPiece extends BuriedTreasurePieces.BuriedTreasurePiece {
        private BoxPiece(BoundingBox box) {
            super(new BlockPos(box.minX(), box.minY(), box.minZ()));
            boundingBox = box;
        }
    }
}
