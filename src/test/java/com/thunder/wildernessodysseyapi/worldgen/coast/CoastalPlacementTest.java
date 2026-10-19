package com.thunder.wildernessodysseyapi.worldgen.coast;

import com.thunder.wildernessodysseyapi.worldgen.biome.ModBiomes;
import com.thunder.wildernessodysseyapi.worldgen.coast.config.CoastalWorldgenConfig;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderOwner;
import net.minecraft.core.HolderSet;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.tags.TagKey;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ProtoChunk;
import net.minecraft.world.level.chunk.UpgradeData;
import net.minecraft.world.level.levelgen.GenerationStep;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.TerrainAdjustment;
import net.minecraft.world.level.levelgen.structure.structures.BuriedTreasureStructure;
import net.neoforged.neoforge.common.ModConfigSpec;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Proxy;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/** Exercises real placement against a bounded world accessor, including actual chunk structure metadata. */
class CoastalPlacementTest {
    private final Map<ModConfigSpec.ConfigValue<?>, Object> previous = new HashMap<>();
    private static final Map<TagKey<Block>, List<Holder<Block>>> ORIGINAL_BLOCK_TAGS = new HashMap<>();
    private static final Map<TagKey<Fluid>, List<Holder<Fluid>>> ORIGINAL_FLUID_TAGS = new HashMap<>();

    @BeforeAll
    static void bootstrapMinecraftRegistries() {
        net.minecraft.SharedConstants.tryDetectVersion();
        net.minecraft.server.Bootstrap.bootStrap();
        // ModDev bootstraps registries, but JUnit does not load a world's data
        // packs. Supply the vanilla tags used by this fixture, then restore them.
        BuiltInRegistries.BLOCK.getTags().forEach(pair ->
                ORIGINAL_BLOCK_TAGS.put(pair.getFirst(), pair.getSecond().stream().toList()));
        BuiltInRegistries.FLUID.getTags().forEach(pair ->
                ORIGINAL_FLUID_TAGS.put(pair.getFirst(), pair.getSecond().stream().toList()));
        var blockTags = new HashMap<>(ORIGINAL_BLOCK_TAGS);
        blockTags.put(BlockTags.DIRT, List.of(Blocks.DIRT.builtInRegistryHolder(),
                Blocks.GRASS_BLOCK.builtInRegistryHolder(), Blocks.COARSE_DIRT.builtInRegistryHolder()));
        blockTags.put(BlockTags.SAND, List.of(Blocks.SAND.builtInRegistryHolder(), Blocks.RED_SAND.builtInRegistryHolder()));
        blockTags.put(BlockTags.BASE_STONE_OVERWORLD, List.of(Blocks.STONE.builtInRegistryHolder(), Blocks.ANDESITE.builtInRegistryHolder()));
        BuiltInRegistries.BLOCK.bindTags(blockTags);
        var fluidTags = new HashMap<>(ORIGINAL_FLUID_TAGS);
        fluidTags.put(FluidTags.WATER, List.of(Fluids.WATER.builtInRegistryHolder(), Fluids.FLOWING_WATER.builtInRegistryHolder()));
        BuiltInRegistries.FLUID.bindTags(fluidTags);
    }

    @AfterAll
    static void restoreTags() {
        BuiltInRegistries.BLOCK.bindTags(ORIGINAL_BLOCK_TAGS);
        BuiltInRegistries.FLUID.bindTags(ORIGINAL_FLUID_TAGS);
    }

    @BeforeEach
    void enableCoasts() throws ReflectiveOperationException {
        config(CoastalWorldgenConfig.ENABLE_BEACH_BIOME_FAMILY, true);
        config(CoastalWorldgenConfig.ENABLE_COASTAL_TERRAIN_BANDS, true);
        config(CoastalWorldgenConfig.ENABLE_COASTAL_DETAILS, true);
        config(CoastalWorldgenConfig.COASTAL_DETAIL_DENSITY, 0.55);
    }

    private void config(ModConfigSpec.ConfigValue<?> value, Object replacement) throws ReflectiveOperationException {
        Field cache = ModConfigSpec.ConfigValue.class.getDeclaredField("cachedValue");
        cache.setAccessible(true);
        if (!previous.containsKey(value)) previous.put(value, cache.get(value));
        cache.set(value, replacement);
    }

    @AfterEach
    void restoreConfig() throws ReflectiveOperationException {
        Field cache = ModConfigSpec.ConfigValue.class.getDeclaredField("cachedValue");
        cache.setAccessible(true);
        for (var entry : previous.entrySet()) {
            cache.set(entry.getKey(), entry.getValue());
        }
    }

    @Test
    void suitableTropicalBackBeachReliablyReceivesPalmsAcrossSeeds() {
        int chunksWithPalms = 0;
        for (int seed = 0; seed < 128; seed++) {
            ShoreFixture shore = new ShoreFixture(seed);
            shore.generate();
            if (shore.writes.values().stream().anyMatch(state -> state.is(Blocks.JUNGLE_LEAVES))) {
                chunksWithPalms++;
            }
        }
        assertTrue(chunksWithPalms >= 64, "Suitable test chunks with palms: " + chunksWithPalms + "/128");
        System.out.println("Suitable tropical fixture chunks with palms: " + chunksWithPalms + "/128");
    }

    @Test
    void referencedStructureProtectsAllSurfaceAndDecorationWrites() {
        ShoreFixture shore = new ShoreFixture(3);
        Structure structure = new BuriedTreasureStructure(new Structure.StructureSettings(
                HolderSet.direct(List.of()), Map.of(), GenerationStep.Decoration.SURFACE_STRUCTURES,
                TerrainAdjustment.NONE));
        shore.chunk.setAllReferences(Map.of(structure,
                new LongOpenHashSet(new long[]{ChunkPos.asLong(9, 0)})));
        shore.generate();
        assertTrue(shore.writes.isEmpty(), "A referenced structure must protect resurfacing and details too");
    }

    @Test
    void tropicalSandContinuesAcrossTheShallowSeabedWithoutReplacingWater() throws ReflectiveOperationException {
        config(CoastalWorldgenConfig.ENABLE_COASTAL_DETAILS, false);
        ShoreFixture shore = new ShoreFixture(42);
        shore.waterLine = 8;
        shore.generate();

        assertEquals(Blocks.SAND.defaultBlockState(), shore.writes.get(new BlockPos(6, 59, 8)));
        assertEquals(Blocks.SANDSTONE.defaultBlockState(), shore.writes.get(new BlockPos(6, 58, 8)));
        assertTrue(shore.writes.keySet().stream().noneMatch(pos -> pos.getX() < 8 && pos.getY() >= 60),
                "The sand veneer must leave the water column intact");
    }

    @Test
    void tropicalSeabedLeavesDeepWaterCavesAndOtherBiomesAlone() throws ReflectiveOperationException {
        config(CoastalWorldgenConfig.ENABLE_COASTAL_DETAILS, false);
        ShoreFixture deep = new ShoreFixture(42);
        deep.waterLine = 8;
        deep.seabedY = 54;
        deep.generate();
        assertTrue(deep.writes.keySet().stream().noneMatch(pos -> pos.getX() < 8));

        ShoreFixture cave = new ShoreFixture(42);
        cave.waterLine = 8;
        cave.hole = new BlockPos(6, 57, 8);
        cave.generate();
        assertFalse(cave.writes.containsKey(new BlockPos(6, 59, 8)), "Do not cap a hollow seabed");

        ShoreFixture rocky = new ShoreFixture(42);
        rocky.waterLine = 8;
        rocky.biome = ModBiomes.ROCKY_COAST_KEY;
        rocky.generate();
        assertTrue(rocky.writes.keySet().stream().noneMatch(pos -> pos.getX() < 8));
    }

    @Test
    void palmsRejectUnsupportedRootsWithoutLeavingPartialTrees() {
        ShoreFixture shore = new ShoreFixture(4);
        shore.hole = new BlockPos(8, 64, 8);
        assertFalse(TropicalPalmPlacer.place(shore.level, shore.hole, 0, 0, 6, 1, 0));
        assertTrue(shore.writes.isEmpty());
    }

    @Test
    void identicalSeedsProduceIdenticalBlocksAndStayInsideTheOriginChunk() {
        ShoreFixture first = new ShoreFixture(101);
        ShoreFixture second = new ShoreFixture(101);
        first.generate();
        second.generate();
        assertFalse(first.writes.isEmpty());
        assertEquals(first.writes, second.writes);
    }

    @Test
    void palmsRejectObstructionsAndChunkEdgesWithoutPartialWrites() {
        ShoreFixture shore = new ShoreFixture(4);
        shore.writes.put(new BlockPos(8, 67, 8), Blocks.CHEST.defaultBlockState());
        Map<BlockPos, BlockState> before = Map.copyOf(shore.writes);
        assertFalse(TropicalPalmPlacer.place(shore.level, new BlockPos(8, 64, 8), 0, 0, 6, 1, 0));
        assertEquals(before, shore.writes);
        assertFalse(TropicalPalmPlacer.place(shore.level, new BlockPos(0, 64, 0), 0, 0, 6, -1, 0));
        assertEquals(before, shore.writes);
    }

    @Test
    void driftwoodRetriesTheCoastTangentInTheOtherDirectionAtAChunkEdge() {
        ShoreFixture shore = new ShoreFixture(5);
        CoastalGenerationArea area = new CoastalGenerationArea(shore.level, 0, 0);
        assertTrue(CoastalDetailPlacer.placeDriftwood(shore.level, area, new BlockPos(8, 64, 15),
                com.thunder.wildernessodysseyapi.watersystem.ocean.coast.CoastalWaveProfile.ShoreType.TEMPERATE,
                area.contact(8, 15), 5));
        assertTrue(shore.writes.size() >= 2 && shore.writes.size() <= 4);
        assertTrue(shore.writes.entrySet().stream().allMatch(entry -> entry.getValue().is(Blocks.OAK_LOG)
                && entry.getKey().getY() == 65 && entry.getKey().getX() == 8));
    }

    @Test
    void tidePoolsRequireAContinuousFloorAndAnIntactRim() {
        ShoreFixture shore = new ShoreFixture(6);
        CoastalGenerationArea area = new CoastalGenerationArea(shore.level, 0, 0);
        shore.hole = new BlockPos(8, 63, 8);
        assertFalse(CoastalDetailPlacer.placeTidePool(shore.level, area, new BlockPos(8, 64, 8)));
        assertTrue(shore.writes.isEmpty());
        shore.hole = null;
        assertFalse(CoastalDetailPlacer.placeTidePool(shore.level, area, new BlockPos(15, 64, 8)));
        assertTrue(shore.writes.isEmpty());
        assertTrue(CoastalDetailPlacer.placeTidePool(shore.level, area, new BlockPos(8, 64, 8)));
        assertEquals(4, shore.writes.size());
        assertTrue(shore.writes.values().stream().allMatch(state -> state.is(Blocks.WATER)));
    }

    @Test
    void unavailableWaterChunksDoNotTriggerFallbackEarthworkOrChunkRequests() {
        ShoreFixture shore = new ShoreFixture(7);
        shore.onlyOriginAvailable = true;
        shore.generate();
        assertTrue(shore.writes.isEmpty());
    }

    @Test
    void allSixProfilesProduceTheirOwnSurfaceMaterials() throws ReflectiveOperationException {
        config(CoastalWorldgenConfig.ENABLE_COASTAL_DETAILS, false);
        Map<ResourceKey<Biome>, net.minecraft.world.level.block.Block> expected = Map.of(
                ModBiomes.TEMPERATE_BEACH_KEY, Blocks.SAND,
                ModBiomes.DUNE_BEACH_KEY, Blocks.SAND,
                ModBiomes.ROCKY_COAST_KEY, Blocks.STONE,
                ModBiomes.COLD_BEACH_KEY, Blocks.GRAVEL,
                ModBiomes.GLACIAL_BEACH_KEY, Blocks.PACKED_ICE,
                ModBiomes.TROPICAL_BEACH_KEY, Blocks.SAND);
        for (var entry : expected.entrySet()) {
            ShoreFixture shore = new ShoreFixture(18);
            shore.biome = entry.getKey();
            shore.generate();
            assertTrue(shore.writes.values().stream().anyMatch(state -> state.is(entry.getValue())),
                    entry.getKey().location().toString());
            assertTrue(shore.heightReads <= 56 * 56, "Height queries must be cached");
            assertTrue(shore.biomeReads <= 24 * 24, "Biome queries must be cached");
        }
    }

    @Test
    void disabledFamilyAndZeroDetailDensityDoNotPlaceAnything() throws ReflectiveOperationException {
        config(CoastalWorldgenConfig.ENABLE_BEACH_BIOME_FAMILY, false);
        ShoreFixture disabled = new ShoreFixture(8);
        disabled.generate();
        assertTrue(disabled.writes.isEmpty());
        config(CoastalWorldgenConfig.ENABLE_BEACH_BIOME_FAMILY, true);
        config(CoastalWorldgenConfig.ENABLE_COASTAL_TERRAIN_BANDS, false);
        config(CoastalWorldgenConfig.COASTAL_DETAIL_DENSITY, 0.0);
        ShoreFixture empty = new ShoreFixture(8);
        empty.generate();
        assertTrue(empty.writes.isEmpty());
    }

    @Test
    void palmAndDriftwoodControlsDisableTheirOwnContent() throws ReflectiveOperationException {
        config(CoastalWorldgenConfig.PALM_DENSITY, 0.0);
        config(CoastalWorldgenConfig.DRIFTWOOD_DENSITY, 0.0);
        for (int seed = 0; seed < 16; seed++) {
            ShoreFixture shore = new ShoreFixture(seed);
            shore.generate();
            assertFalse(shore.writes.values().stream().anyMatch(state -> state.is(Blocks.JUNGLE_LOG)
                    || state.is(Blocks.JUNGLE_LEAVES)));
        }
    }

    @Test
    void adjacentChunksAcrossNegativeCoordinatesKeepGentleTerrainTransitions() throws ReflectiveOperationException {
        config(CoastalWorldgenConfig.ENABLE_COASTAL_DETAILS, false);
        for (var biome : List.of(ModBiomes.TEMPERATE_BEACH_KEY, ModBiomes.DUNE_BEACH_KEY,
                ModBiomes.ROCKY_COAST_KEY, ModBiomes.COLD_BEACH_KEY,
                ModBiomes.GLACIAL_BEACH_KEY, ModBiomes.TROPICAL_BEACH_KEY)) {
            ShoreFixture north = new ShoreFixture(33, -16);
            ShoreFixture south = new ShoreFixture(33, 0);
            north.biome = biome;
            south.biome = biome;
            north.generate();
            south.generate();
            for (int x = 0; x < 16; x++) {
                assertTrue(Math.abs(north.solidTop(x, -1) - south.solidTop(x, 0)) <= 1,
                        "Chunk transition at " + x + " for " + biome.location());
            }
        }
    }

    @Test
    void terrainDoesNotExposeACaveOrReplaceAnUnregisteredBuildingBlock() throws ReflectiveOperationException {
        config(CoastalWorldgenConfig.ENABLE_COASTAL_DETAILS, false);
        ShoreFixture shore = new ShoreFixture(34);
        shore.hole = new BlockPos(2, 62, 8);
        BlockPos building = new BlockPos(4, 64, 8);
        shore.writes.put(building, Blocks.CHEST.defaultBlockState());
        shore.generate();
        assertFalse(shore.writes.containsKey(new BlockPos(2, 64, 8)), "Cave roof must remain intact");
        assertEquals(Blocks.CHEST.defaultBlockState(), shore.writes.get(building));
    }

    @Test
    void driftwoodIsDiscoverableAcrossSuitableTropicalChunks() {
        int withDriftwood = 0;
        for (int seed = 0; seed < 128; seed++) {
            ShoreFixture shore = new ShoreFixture(seed);
            shore.generate();
            if (shore.writes.values().stream().anyMatch(state -> state.is(Blocks.JUNGLE_LOG)
                    && state.getValue(net.minecraft.world.level.block.RotatedPillarBlock.AXIS) != net.minecraft.core.Direction.Axis.Y)) {
                withDriftwood++;
            }
        }
        assertTrue(withDriftwood >= 32, "Chunks with fallen logs: " + withDriftwood + "/128");
        System.out.println("Suitable tropical fixture chunks with driftwood: " + withDriftwood + "/128");
    }

    @Test
    void seaStacksPreflightEveryOceanColumnAndRejectObstructionsWithoutPartialWrites() {
        ShoreFixture shore = new ShoreFixture(36);
        shore.waterLine = 8;
        CoastalGenerationArea area = new CoastalGenerationArea(shore.level, 0, 0);
        assertTrue(CoastalDetailPlacer.placeSeaStack(shore.level, area, area.contact(8, 8), 36));
        assertFalse(shore.writes.isEmpty());
        assertTrue(shore.writes.entrySet().stream().allMatch(entry -> entry.getKey().getX() < 8
                && (entry.getValue().is(Blocks.STONE) || entry.getValue().is(Blocks.ANDESITE))));

        ShoreFixture blocked = new ShoreFixture(36);
        blocked.waterLine = 8;
        CoastalGenerationArea blockedArea = new CoastalGenerationArea(blocked.level, 0, 0);
        BlockPos obstacle = new BlockPos(5, 63, 8);
        blocked.writes.put(obstacle, Blocks.CHEST.defaultBlockState());
        assertFalse(CoastalDetailPlacer.placeSeaStack(blocked.level, blockedArea, blockedArea.contact(8, 8), 36));
        assertEquals(Map.of(obstacle, Blocks.CHEST.defaultBlockState()), blocked.writes);
    }

    @Test
    void surfaceChangesFadeAtTheActualLandBiomeBoundary() throws ReflectiveOperationException {
        config(CoastalWorldgenConfig.ENABLE_COASTAL_DETAILS, false);
        ShoreFixture shore = new ShoreFixture(37);
        shore.inlandForest = true;
        shore.generate();
        assertFalse(shore.writes.isEmpty());
        assertTrue(shore.writes.keySet().stream().allMatch(pos -> pos.getX() < 11),
                "No grading on the forest side or the last coastal column");
    }

    static final class ShoreFixture {
        final Map<BlockPos, BlockState> writes = new HashMap<>();
        final ProtoChunk chunk;
        final int minimumZ;
        BlockPos hole;
        boolean onlyOriginAvailable;
        int heightReads;
        int biomeReads;
        int waterLine;
        int seabedY = 59;
        boolean inlandForest;
        ResourceKey<Biome> biome = ModBiomes.TROPICAL_BEACH_KEY;
        final WorldGenLevel level;

        ShoreFixture(long seed) {
            this(seed, 0);
        }

        ShoreFixture(long seed, int minimumZ) {
            this.minimumZ = minimumZ;
            chunk = new ProtoChunk(new ChunkPos(0, minimumZ >> 4), UpgradeData.EMPTY,
                    LevelHeightAccessor.create(0, 0), null, null);
            level = (WorldGenLevel) Proxy.newProxyInstance(WorldGenLevel.class.getClassLoader(),
                    new Class<?>[]{WorldGenLevel.class}, (proxy, method, args) -> switch (method.getName()) {
                        case "getSeed" -> seed;
                        case "getSeaLevel" -> 63;
                        case "getMinBuildHeight" -> 0;
                        case "getMaxBuildHeight" -> 256;
                        case "hasChunk" -> onlyOriginAvailable ? (int) args[0] == 0 && (int) args[1] == 0
                                : (int) args[0] >= -2 && (int) args[0] <= 2
                                && (int) args[1] >= -2 && (int) args[1] <= 2;
                        case "getHeight" -> {
                            heightReads++;
                            yield (int) args[1] < waterLine
                                    ? args[0] == net.minecraft.world.level.levelgen.Heightmap.Types.OCEAN_FLOOR_WG ? seabedY + 1 : 63 : 65;
                        }
                        case "ensureCanWrite" -> inside((BlockPos) args[0]);
                        case "getChunk" -> {
                            boolean own = args[0] instanceof BlockPos pos ? inside(pos)
                                    : (int) args[0] == 0 && (int) args[1] == (minimumZ >> 4);
                            if (!own) throw new AssertionError("Unexpected chunk request");
                            yield chunk;
                        }
                        case "getBlockState" -> state((BlockPos) args[0]);
                        case "getFluidState" -> state((BlockPos) args[0]).getFluidState();
                        case "getBiome" -> {
                            biomeReads++;
                            yield Holder.Reference.createStandAlone(new HolderOwner<>() {},
                                    inlandForest && ((BlockPos) args[0]).getX() >= 12 ? net.minecraft.world.level.biome.Biomes.FOREST : biome);
                        }
                        case "setBlock" -> {
                            BlockPos pos = ((BlockPos) args[0]).immutable();
                            assertTrue(inside(pos), "Out-of-chunk write: " + pos);
                            writes.put(pos, (BlockState) args[1]);
                            yield true;
                        }
                        default -> throw new AssertionError("Unexpected world access: " + method);
                    });
        }

        private BlockState state(BlockPos pos) {
            if (pos.equals(hole)) return Blocks.AIR.defaultBlockState();
            BlockState written = writes.get(pos);
            if (written != null) return written;
            if (pos.getX() < waterLine && pos.getY() > seabedY && pos.getY() <= 62) return Blocks.WATER.defaultBlockState();
            if (pos.getY() > (pos.getX() < waterLine ? 62 : 64)) return Blocks.AIR.defaultBlockState();
            return (pos.getY() == 64 ? Blocks.GRASS_BLOCK : Blocks.STONE).defaultBlockState();
        }

        void generate() {
            new CoastalTerrainFeature().place(new FeaturePlaceContext<>(Optional.empty(), level, null,
                    RandomSource.create(1), new BlockPos(0, 64, minimumZ), NoneFeatureConfiguration.INSTANCE));
        }

        private int solidTop(int x, int z) {
            for (int y = 72; y >= 60; y--) {
                BlockState state = state(new BlockPos(x, y, z));
                if (!state.isAir() && !state.is(Blocks.SNOW)) return y;
            }
            throw new AssertionError("Missing terrain");
        }

        private boolean inside(BlockPos pos) {
            return pos.getX() >= 0 && pos.getX() < 16 && pos.getZ() >= minimumZ && pos.getZ() < minimumZ + 16;
        }
    }
}
