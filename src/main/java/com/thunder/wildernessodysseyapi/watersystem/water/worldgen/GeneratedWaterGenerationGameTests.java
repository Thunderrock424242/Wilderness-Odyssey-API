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
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.GlowSquid;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.animal.TropicalFish;
import net.minecraft.world.entity.animal.WaterAnimal;
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
import net.minecraft.world.level.levelgen.feature.configurations.CountConfiguration;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;
import net.minecraft.world.level.levelgen.feature.configurations.ProbabilityFeatureConfiguration;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.util.valueproviders.ConstantInt;
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
        BlockPos kelpPos = sourcePos.above(4);

        proto.setBlockState(sourcePos, Blocks.WATER.defaultBlockState(), false);
        proto.setBlockState(flowPos, Fluids.FLOWING_WATER.getFlowing(3, false).createLegacyBlock(), false);
        proto.setBlockState(fallingPos, Fluids.FLOWING_WATER.getFlowing(8, true).createLegacyBlock(), false);
        var waterloggedFence = Blocks.OAK_FENCE.defaultBlockState()
                .setValue(BlockStateProperties.WATERLOGGED, true);
        proto.setBlockState(waterloggedPos, waterloggedFence, false);
        proto.setBlockState(kelpPos, Blocks.WATER.defaultBlockState(), false);
        proto.setBlockState(kelpPos, Blocks.KELP.defaultBlockState(), false);

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
        helper.assertTrue(generated.spanAt(kelpPos) != null,
                "Generated kelp removed its underlying compact water span");
        BlockPos wallCoralPos = kelpPos.offset(1, 0, 0);
        proto.setBlockState(wallCoralPos, Blocks.WATER.defaultBlockState(), false);
        proto.setBlockState(wallCoralPos, Blocks.BRAIN_CORAL_WALL_FAN.defaultBlockState()
                .setValue(BlockStateProperties.WATERLOGGED, true), false);
        helper.assertTrue(generated.spanAt(wallCoralPos) != null,
                "Generated wall coral removed its underlying compact water span");
        helper.assertTrue(generated.spanAt(waterloggedPos) == null,
                "General waterlogged host was incorrectly recorded as generated water");
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

    /** Verifies natural aquatic flora and the common surface-fauna predicates accept custom water. */
    @GameTest(template = "empty", timeoutTicks = 400)
    public static void aquaticFloraAndFaunaAcceptWildernessWater(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        int seaLevel = level.getSeaLevel();
        BlockPos basinCenter = new BlockPos(helper.absolutePos(new BlockPos(2, 2, 2)).getX(),
                seaLevel - 5, helper.absolutePos(new BlockPos(2, 2, 2)).getZ());
        var wildernessWater = WildernessFluidRegistry.WILDERNESS_WATER.get()
                .defaultFluidState().createLegacyBlock();

        // A shallow bounded basin gives the vanilla OCEAN_FLOOR heightmap and
        // random feature offsets the same inputs they receive in a real ocean.
        for (int z = -10; z <= 10; z++) {
            for (int x = -10; x <= 10; x++) {
                BlockPos floor = basinCenter.offset(x, -1, z);
                level.setBlock(floor, Blocks.STONE.defaultBlockState(), 2);
                for (int y = 0; y <= 4; y++) {
                    level.setBlock(basinCenter.offset(x, y, z), wildernessWater, 2);
                }
            }
        }

        boolean kelpPlaced = Feature.KELP.place(new FeaturePlaceContext<>(
                Optional.empty(), level, level.getChunkSource().getGenerator(), RandomSource.create(17L),
                basinCenter, NoneFeatureConfiguration.INSTANCE));
        boolean seagrassPlaced = Feature.SEAGRASS.place(new FeaturePlaceContext<>(
                Optional.empty(), level, level.getChunkSource().getGenerator(), RandomSource.create(23L),
                basinCenter, new ProbabilityFeatureConfiguration(0.0F)));
        boolean seaPicklePlaced = Feature.SEA_PICKLE.place(new FeaturePlaceContext<>(
                Optional.empty(), level, level.getChunkSource().getGenerator(), RandomSource.create(29L),
                basinCenter, new CountConfiguration(ConstantInt.of(12))));
        boolean coralPlaced = Feature.CORAL_TREE.place(new FeaturePlaceContext<>(
                Optional.empty(), level, level.getChunkSource().getGenerator(), RandomSource.create(30L),
                basinCenter.offset(7, 0, 7), NoneFeatureConfiguration.INSTANCE));

        // Flora placement intentionally mutates its sampled columns. Use a
        // dedicated reset column so the fauna assertions test water identity,
        // not whether kelp happened to occupy the required block above.
        BlockPos spawnPos = basinCenter.offset(-8, 2, -8);
        level.setBlock(spawnPos.below(), wildernessWater, 2);
        level.setBlock(spawnPos, wildernessWater, 2);
        level.setBlock(spawnPos.above(), wildernessWater, 2);
        helper.assertTrue(spawnPos.getY() >= seaLevel - 13 && spawnPos.getY() <= seaLevel,
                "Fauna probe is outside vanilla's surface-water height band");
        helper.assertTrue(level.getFluidState(spawnPos.below()).is(net.minecraft.tags.FluidTags.WATER),
                "Wilderness water is missing from FluidTags.WATER at fauna probe");
        helper.assertTrue(NaturalAquaticWaterCompatibility.matchesRequestedBlock(
                        level.getBlockState(spawnPos.above()), Blocks.WATER),
                "Fauna probe block above is not standalone Wilderness water");
        boolean codCanSpawn = WaterAnimal.checkSurfaceWaterAnimalSpawnRules(
                EntityType.COD, level, MobSpawnType.NATURAL, spawnPos, RandomSource.create(31L));
        boolean tropicalFishCanSpawn = TropicalFish.checkTropicalFishSpawnRules(
                EntityType.TROPICAL_FISH, level, MobSpawnType.NATURAL, spawnPos, RandomSource.create(37L));

        helper.assertTrue(kelpPlaced, "KelpFeature rejected standalone Wilderness water");
        helper.assertTrue(seagrassPlaced, "SeagrassFeature rejected standalone Wilderness water");
        helper.assertTrue(seaPicklePlaced, "SeaPickleFeature rejected standalone Wilderness water");
        helper.assertTrue(coralPlaced, "CoralFeature rejected standalone Wilderness water");
        helper.assertTrue(codCanSpawn, "Surface water animal predicate rejected Wilderness water");
        helper.assertTrue(tropicalFishCanSpawn, "Tropical fish predicate rejected Wilderness water");

        // Glow squid also use an exact water block check, but retain vanilla's
        // darkness and deep-water constraints. Give the light engine time to
        // settle a sealed chamber before evaluating the real predicate.
        BlockPos darkCenter = basinCenter.offset(0, -36, 0);
        for (int y = -2; y <= 2; y++) {
            for (int z = -2; z <= 2; z++) {
                for (int x = -2; x <= 2; x++) {
                    boolean shell = Math.abs(x) == 2 || Math.abs(y) == 2 || Math.abs(z) == 2;
                    level.setBlock(darkCenter.offset(x, y, z),
                            shell ? Blocks.STONE.defaultBlockState() : Blocks.AIR.defaultBlockState(), 3);
                }
            }
        }
        level.setBlock(darkCenter, wildernessWater, 3);
        helper.runAfterDelay(20, () -> {
            boolean glowSquidCanSpawn = GlowSquid.checkGlowSquidSpawnRules(
                    EntityType.GLOW_SQUID, level, MobSpawnType.NATURAL,
                    darkCenter, RandomSource.create(41L));
            helper.assertTrue(glowSquidCanSpawn,
                    "Glow squid predicate rejected dark standalone Wilderness water");
            helper.succeed();
        });
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
