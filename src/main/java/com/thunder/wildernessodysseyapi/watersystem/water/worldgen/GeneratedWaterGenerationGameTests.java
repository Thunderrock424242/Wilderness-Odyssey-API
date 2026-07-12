package com.thunder.wildernessodysseyapi.watersystem.water.worldgen;

import com.thunder.wildernessodysseyapi.core.ModAttachments;
import com.thunder.wildernessodysseyapi.core.ModConstants;
import com.thunder.wildernessodysseyapi.watersystem.water.fluid.WildernessFluidRegistry;
import com.thunder.wildernessodysseyapi.watersystem.water.volume.GeneratedWaterChunk;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderSet;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.biome.MultiNoiseBiomeSource;
import net.minecraft.world.level.biome.MultiNoiseBiomeSourceParameterLists;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.ProtoChunk;
import net.minecraft.world.level.chunk.UpgradeData;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import net.minecraft.world.level.levelgen.NoiseGeneratorSettings;
import net.minecraft.world.level.levelgen.blending.Blender;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.SpringConfiguration;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.Optional;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Integration coverage for generation-only Wilderness water boundaries.
 *
 * <p>Production never scans completed chunks. The first test intentionally
 * scans newly generated test chunks only as validation that noise/aquifer
 * writes cannot leave standalone vanilla water behind.</p>
 */
@GameTestHolder(ModConstants.MOD_ID)
@PrefixGameTestTemplate(false)
public final class GeneratedWaterGenerationGameTests {

    private GeneratedWaterGenerationGameTests() {
    }

    /** Verifies natural noise chunks, including ocean/river/aquifer water, store only custom fluid. */
    @GameTest(template = "empty", timeoutTicks = 1_200)
    public static void naturalNoiseChunksContainOnlyWildernessWater(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        var biomeParameters = level.registryAccess()
                .registryOrThrow(Registries.MULTI_NOISE_BIOME_SOURCE_PARAMETER_LIST)
                .getHolderOrThrow(MultiNoiseBiomeSourceParameterLists.OVERWORLD);
        var noiseSettings = level.registryAccess()
                .registryOrThrow(Registries.NOISE_SETTINGS)
                .getHolderOrThrow(NoiseGeneratorSettings.OVERWORLD);
        NoiseBasedChunkGenerator generator = new NoiseBasedChunkGenerator(
                MultiNoiseBiomeSource.createFromPreset(biomeParameters), noiseSettings);
        var biomeRegistry = level.registryAccess().registryOrThrow(Registries.BIOME);
        List<ProtoChunk> chunks = new ArrayList<>();
        List<CompletableFuture<?>> generation = new ArrayList<>();

        // Generate a bounded 3x3 sample through the real Overworld noise and
        // aquifer pipeline. This invokes the direct section-write mixin that a
        // normal GameTest superflat level cannot exercise by itself.
        for (int chunkZ = -1; chunkZ <= 1; chunkZ++) {
            for (int chunkX = -1; chunkX <= 1; chunkX++) {
                ChunkPos chunkPos = new ChunkPos(chunkX, chunkZ);
                ProtoChunk proto = new ProtoChunk(chunkPos, UpgradeData.EMPTY, level, biomeRegistry, null);
                chunks.add(proto);
                CompletableFuture<?> future = generator.createBiomes(
                        level.getChunkSource().randomState(),
                        Blender.empty(),
                        level.structureManager(),
                        proto
                ).thenCompose(ignored -> generator.fillFromNoise(
                            Blender.empty(),
                            level.getChunkSource().randomState(),
                            level.structureManager(),
                            proto
                    ));
                generation.add(future);
            }
        }
        CompletableFuture.allOf(generation.toArray(CompletableFuture[]::new))
                .whenComplete((ignored, failure) -> level.getServer().execute(() -> {
                    if (failure != null) {
                        helper.fail("Noise generation failed: " + failure.getMessage());
                        return;
                    }
                    int customCells = 0;
                    for (ProtoChunk chunk : chunks) {
                        customCells += validateChunk(helper, chunk);
                    }
                    helper.assertTrue(customCells > 0,
                            "Generated test chunks did not contain any water to validate");
                    helper.succeed();
                }));
    }

    /** Verifies every ProtoChunk-based generator category shares the same exact mapping boundary. */
    @GameTest(template = "empty")
    public static void protoBoundaryCoversFlatLakeCarverAndFeatureWrites(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        var biomeRegistry = level.registryAccess().registryOrThrow(Registries.BIOME);
        ChunkPos chunkPos = new ChunkPos(30_001, 30_001);
        ProtoChunk proto = new ProtoChunk(chunkPos, UpgradeData.EMPTY, level, biomeRegistry, null);
        BlockPos sourcePos = new BlockPos(chunkPos.getMinBlockX(), 63, chunkPos.getMinBlockZ());
        BlockPos flowPos = sourcePos.above();
        BlockPos fallingPos = sourcePos.above(2);
        BlockPos waterloggedPos = sourcePos.above(3);

        proto.setBlockState(sourcePos, Blocks.WATER.defaultBlockState(), false);
        proto.setBlockState(flowPos, Fluids.FLOWING_WATER.getFlowing(3, false).createLegacyBlock(), false);
        proto.setBlockState(fallingPos, Fluids.FLOWING_WATER.getFlowing(8, true).createLegacyBlock(), false);
        var waterloggedFence = Blocks.OAK_FENCE.defaultBlockState()
                .setValue(BlockStateProperties.WATERLOGGED, true);
        proto.setBlockState(waterloggedPos, waterloggedFence, false);

        helper.assertTrue(GenerationWaterStateMapper.isWildernessWater(proto.getBlockState(sourcePos)),
                "Source water was not mapped at ProtoChunk boundary");
        helper.assertTrue(GenerationWaterStateMapper.isWildernessWater(proto.getBlockState(flowPos)),
                "Flowing water was not mapped at ProtoChunk boundary");
        helper.assertTrue(proto.getFluidState(flowPos).getAmount() == 3,
                "Flowing amount changed during generation mapping");
        helper.assertFalse(proto.getFluidState(flowPos).getValue(BlockStateProperties.FALLING),
                "Non-falling state changed during generation mapping");
        helper.assertTrue(proto.getFluidState(fallingPos).getAmount() == 8,
                "Falling amount changed during generation mapping");
        helper.assertTrue(proto.getFluidState(fallingPos).getValue(BlockStateProperties.FALLING),
                "Falling state changed during generation mapping");
        helper.assertTrue(proto.getBlockState(waterloggedPos).equals(waterloggedFence),
                "Waterlogged host block was incorrectly replaced");

        GeneratedWaterChunk generated = proto.getData(ModAttachments.GENERATED_WATER);
        helper.assertTrue(generated.spanAt(sourcePos) != null && generated.spanAt(flowPos) != null
                        && generated.spanAt(fallingPos) != null,
                "Mapped ProtoChunk writes did not record generated metadata");
        GeneratedWaterChunk reloaded = new GeneratedWaterChunk();
        reloaded.deserializeNBT(level.registryAccess(), generated.serializeNBT(level.registryAccess()));
        helper.assertTrue(reloaded.spanAt(sourcePos) != null && reloaded.spanAt(flowPos) != null
                        && reloaded.spanAt(fallingPos) != null,
                "Generated metadata did not survive save/reload serialization");
        helper.succeed();
    }

    /** Verifies a vanilla-configured spring places and schedules the custom fluid identity. */
    @GameTest(template = "empty")
    public static void springFeatureUsesWildernessFluid(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos origin = helper.absolutePos(new BlockPos(2, 2, 2));
        level.setBlock(origin.above(), Blocks.STONE.defaultBlockState(), 3);
        level.setBlock(origin.below(), Blocks.STONE.defaultBlockState(), 3);
        level.setBlock(origin.west(), Blocks.STONE.defaultBlockState(), 3);
        level.setBlock(origin.east(), Blocks.STONE.defaultBlockState(), 3);
        level.setBlock(origin.north(), Blocks.STONE.defaultBlockState(), 3);
        level.setBlock(origin.south(), Blocks.STONE.defaultBlockState(), 3);

        SpringConfiguration configuration = new SpringConfiguration(
                Fluids.FLOWING_WATER.getFlowing(8, true),
                true,
                5,
                0,
                HolderSet.direct(Blocks.STONE.builtInRegistryHolder())
        );
        boolean placed = Feature.SPRING.place(new FeaturePlaceContext<>(
                Optional.empty(),
                level,
                level.getChunkSource().getGenerator(),
                RandomSource.create(42L),
                origin,
                configuration
        ));

        helper.assertTrue(placed, "Spring feature did not place in the test rock pocket");
        helper.assertTrue(level.getFluidState(origin).getType().isSame(
                        WildernessFluidRegistry.FLOWING_WILDERNESS_WATER.get()),
                "Spring feature stored vanilla water instead of Wilderness water");
        helper.succeed();
    }

    private static int validateChunk(GameTestHelper helper, ProtoChunk chunk) {
        ChunkPos chunkPos = chunk.getPos();
        GeneratedWaterChunk generated = chunk.getExistingData(ModAttachments.GENERATED_WATER).orElse(null);
        int customCells = 0;
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        for (int y = chunk.getMinBuildHeight(); y < chunk.getMaxBuildHeight(); y++) {
            for (int localZ = 0; localZ < 16; localZ++) {
                for (int localX = 0; localX < 16; localX++) {
                    cursor.set(chunkPos.getMinBlockX() + localX, y, chunkPos.getMinBlockZ() + localZ);
                    var state = chunk.getBlockState(cursor);
                    helper.assertFalse(state.is(Blocks.WATER),
                            "Newly generated chunk stored standalone minecraft:water at " + cursor);
                    if (GenerationWaterStateMapper.isWildernessWater(state)) {
                        customCells++;
                        helper.assertTrue(generated != null && generated.spanAt(cursor) != null,
                                "Physical Wilderness water is missing generated metadata at " + cursor);
                    }
                }
            }
        }
        return customCells;
    }
}
