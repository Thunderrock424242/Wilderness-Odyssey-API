package com.thunder.wildernessodysseyapi.watersystem.water.compat.vanilla;

import com.mojang.authlib.GameProfile;
import com.thunder.wildernessodysseyapi.core.ModConstants;
import com.thunder.wildernessodysseyapi.watersystem.water.config.WaterSimulationConfig;
import com.thunder.wildernessodysseyapi.watersystem.water.fluid.WildernessFluidRegistry;
import com.thunder.wildernessodysseyapi.watersystem.water.volume.CanonicalWater;
import com.thunder.wildernessodysseyapi.watersystem.water.volume.WaterVolumeChunk;
import com.thunder.wildernessodysseyapi.watersystem.water.volume.WildernessWaterAuthority;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.cauldron.CauldronInteraction;
import net.minecraft.core.dispenser.BlockSource;
import net.minecraft.core.dispenser.DispenseItemBehavior;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Bucketable;
import net.minecraft.world.entity.animal.Cod;
import net.minecraft.world.item.BucketItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.BucketPickup;
import net.minecraft.world.level.block.DispenserBlock;
import net.minecraft.world.level.block.LayeredCauldronBlock;
import net.minecraft.world.level.block.entity.DispenserBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.common.Tags;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.UUID;

/** In-world regression coverage for player and automated water containers. */
@GameTestHolder(ModConstants.MOD_ID)
@PrefixGameTestTemplate(false)
public final class CanonicalWaterBucketGameTests {

    private static final int PROJECTED_FLAGS =
            WaterVolumeChunk.FLAG_COMPATIBILITY_PROJECTED | WaterVolumeChunk.FLAG_SLEEPING;

    private CanonicalWaterBucketGameTests() {
    }

    /** A visually-source projection cannot create a bucket from 4,095 units. */
    @GameTest(template = "empty")
    public static void playerPickupRequiresExactCanonicalVolume(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos position = helper.absolutePos(new BlockPos(2, 2, 2));
        var player = helper.makeMockPlayer(GameType.SURVIVAL);

        setCanonical(level, position, WaterVolumeChunk.UNITS_PER_BLOCK - 1);
        BlockState partialProjection = level.getBlockState(position);
        helper.assertTrue(
                partialProjection.getFluidState().isSource(),
                "Regression fixture must look like a source despite missing one authority unit"
        );
        ItemStack rejected = ((BucketPickup) partialProjection.getBlock()).pickupBlock(
                player,
                level,
                position,
                partialProjection
        );
        helper.assertTrue(
                rejected.isEmpty()
                        && WildernessWaterAuthority.getWaterAmount(level, position)
                        == WaterVolumeChunk.UNITS_PER_BLOCK - 1,
                "Partial canonical source produced a full bucket or lost residual water"
        );

        setCanonical(level, position, WaterVolumeChunk.UNITS_PER_BLOCK);
        BlockState fullProjection = level.getBlockState(position);
        ItemStack committed = ((BucketPickup) fullProjection.getBlock()).pickupBlock(
                player,
                level,
                position,
                fullProjection
        );
        helper.assertTrue(
                committed.is(Items.WATER_BUCKET)
                        && WildernessWaterAuthority.getWaterAmount(level, position) == 0
                        && level.getBlockState(position).isAir(),
                "Exact player pickup did not atomically exchange one block for a vanilla-facing bucket"
        );
        helper.succeed();
    }

    /** Empty-bucket dispensers share the same exact-volume pickup transaction. */
    @GameTest(template = "empty")
    public static void dispenserPickupRequiresExactCanonicalVolume(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos dispenserPos = helper.absolutePos(new BlockPos(2, 2, 2));
        BlockPos waterPos = dispenserPos.east();
        BlockSource source = placeDispenser(level, dispenserPos, Direction.EAST);
        DispenseItemBehavior behavior = DispenserBlock.DISPENSER_REGISTRY.get(Items.BUCKET);
        helper.assertTrue(behavior != null, "Vanilla empty-bucket dispenser behavior is missing");

        setCanonical(level, waterPos, WaterVolumeChunk.UNITS_PER_BLOCK - 1);
        ItemStack rejected = behavior.dispense(source, new ItemStack(Items.BUCKET));
        helper.assertTrue(
                !rejected.is(Items.WATER_BUCKET)
                        && WildernessWaterAuthority.getWaterAmount(level, waterPos)
                        == WaterVolumeChunk.UNITS_PER_BLOCK - 1,
                "Dispenser duplicated a bucket from a partial source"
        );

        setCanonical(level, waterPos, WaterVolumeChunk.UNITS_PER_BLOCK);
        ItemStack committed = behavior.dispense(source, new ItemStack(Items.BUCKET));
        helper.assertTrue(
                committed.is(Items.WATER_BUCKET)
                        && WildernessWaterAuthority.getWaterAmount(level, waterPos) == 0,
                "Dispenser could not atomically pick up one exact canonical source"
        );
        helper.succeed();
    }

    /** The custom full bucket pours through a dispenser and never overwrites finite water. */
    @GameTest(template = "empty")
    public static void customBucketDispenserPlacesOneExactBlock(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos dispenserPos = helper.absolutePos(new BlockPos(2, 2, 2));
        BlockPos targetPos = dispenserPos.east();
        BlockSource source = placeDispenser(level, dispenserPos, Direction.EAST);
        VanillaWaterBucketCompatibility.bootstrap();
        DispenseItemBehavior behavior = DispenserBlock.DISPENSER_REGISTRY.get(
                WildernessFluidRegistry.WILDERNESS_WATER_BUCKET.get()
        );

        level.setBlock(targetPos, Blocks.AIR.defaultBlockState(), 3);
        ItemStack result = behavior.dispense(
                source,
                new ItemStack(WildernessFluidRegistry.WILDERNESS_WATER_BUCKET.get())
        );
        helper.assertTrue(
                result.is(Items.BUCKET)
                        && WildernessWaterAuthority.getWaterAmount(level, targetPos)
                        == WaterVolumeChunk.UNITS_PER_BLOCK
                        && level.getBlockState(targetPos).is(
                        WildernessFluidRegistry.WILDERNESS_WATER_BLOCK.get()),
                "Custom bucket dispenser did not exchange one bucket for one canonical block"
        );

        ItemStack secondResult = behavior.dispense(
                source,
                new ItemStack(WildernessFluidRegistry.WILDERNESS_WATER_BUCKET.get())
        );
        helper.assertTrue(
                !secondResult.is(Items.BUCKET)
                        && WildernessWaterAuthority.getWaterAmount(level, targetPos)
                        == WaterVolumeChunk.UNITS_PER_BLOCK,
                "Second dispenser pour consumed a bucket into an already-full finite cell"
        );
        helper.succeed();
    }

    /** Exact-item vanilla contracts recognize the custom bucket end to end. */
    @GameTest(template = "empty")
    public static void customBucketSupportsWaterloggingCauldronsAndFish(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BucketItem customBucket = WildernessFluidRegistry.WILDERNESS_WATER_BUCKET.get();
        helper.assertTrue(
                new ItemStack(customBucket).is(Tags.Items.BUCKETS_WATER),
                "Custom bucket is missing the standard common water-bucket tag"
        );

        BlockPos fencePos = helper.absolutePos(new BlockPos(2, 2, 2));
        level.setBlock(fencePos, Blocks.OAK_FENCE.defaultBlockState(), 3);
        boolean waterlogged = customBucket.emptyContents(
                null,
                level,
                fencePos,
                null,
                new ItemStack(customBucket)
        );
        helper.assertTrue(
                waterlogged
                        && level.getBlockState(fencePos).getValue(BlockStateProperties.WATERLOGGED)
                        && level.getFluidState(fencePos).is(Fluids.WATER)
                        && WildernessWaterAuthority.getWaterAmount(level, fencePos) == 0,
                "Custom bucket did not preserve vanilla waterlogged-host storage"
        );

        var cauldronPlayer = helper.makeMockPlayer(GameType.SURVIVAL);
        BlockPos cauldronPos = helper.absolutePos(new BlockPos(4, 2, 2));
        BlockState emptyCauldron = Blocks.CAULDRON.defaultBlockState();
        level.setBlock(cauldronPos, emptyCauldron, 3);
        ItemStack cauldronBucket = new ItemStack(customBucket);
        cauldronPlayer.setItemInHand(InteractionHand.MAIN_HAND, cauldronBucket);
        CauldronInteraction.EMPTY.map().get(customBucket).interact(
                emptyCauldron,
                level,
                cauldronPos,
                cauldronPlayer,
                InteractionHand.MAIN_HAND,
                cauldronBucket
        );
        helper.assertTrue(
                level.getBlockState(cauldronPos).is(Blocks.WATER_CAULDRON)
                        && level.getBlockState(cauldronPos).getValue(LayeredCauldronBlock.LEVEL) == 3
                        && cauldronPlayer.getItemInHand(InteractionHand.MAIN_HAND).is(Items.BUCKET),
                "Custom bucket did not use vanilla full-water cauldron semantics"
        );

        // A detached server player exercises vanilla's criteria path without
        // firing optional-mod login sync packets unrelated to bucket behavior.
        ServerPlayer fishPlayer = new ServerPlayer(
                level.getServer(),
                level,
                new GameProfile(UUID.randomUUID(), "bucket-test-player"),
                ClientInformation.createDefault()
        );
        fishPlayer.setGameMode(GameType.SURVIVAL);
        fishPlayer.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(customBucket));
        Cod cod = helper.spawnWithNoFreeWill(EntityType.COD, new BlockPos(6, 2, 2));
        var pickup = Bucketable.bucketMobPickup(fishPlayer, InteractionHand.MAIN_HAND, cod);
        helper.assertTrue(
                pickup.isPresent()
                        && fishPlayer.getItemInHand(InteractionHand.MAIN_HAND).is(Items.COD_BUCKET)
                        && cod.isRemoved(),
                "Custom water bucket could not collect a vanilla bucketable fish"
        );
        helper.succeed();
    }

    /** Disabling translation cannot expose finite projections to vanilla duplication paths. */
    @GameTest(template = "empty")
    public static void disabledCompatibilityStillProtectsOwnedVolume(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos pickupPos = helper.absolutePos(new BlockPos(2, 2, 2));
        BlockPos placementPos = helper.absolutePos(new BlockPos(4, 2, 2));
        BlockPos vanillaPos = helper.absolutePos(new BlockPos(6, 2, 2));
        boolean previousBucketCompat = WaterSimulationConfig.ENABLE_VANILLA_BUCKET_COMPAT.get();
        boolean previousFluidCompat = WaterSimulationConfig.ENABLE_FLUID_HANDLER_COMPAT.get();

        WaterSimulationConfig.ENABLE_VANILLA_BUCKET_COMPAT.set(false);
        try {
            // The guard must not depend on projection-write reconciliation.
            for (boolean fluidCompat : new boolean[]{true, false}) {
                WaterSimulationConfig.ENABLE_FLUID_HANDLER_COMPAT.set(fluidCompat);

                setCanonical(level, pickupPos, WaterVolumeChunk.UNITS_PER_BLOCK);
                BlockState projection = level.getBlockState(pickupPos);
                ItemStack rejectedPickup = ((BucketPickup) projection.getBlock()).pickupBlock(
                        null,
                        level,
                        pickupPos,
                        projection
                );
                helper.assertTrue(
                        rejectedPickup.isEmpty()
                                && WildernessWaterAuthority.getWaterAmount(level, pickupPos)
                                == WaterVolumeChunk.UNITS_PER_BLOCK,
                        "Disabled compatibility allowed vanilla to duplicate an owned source; "
                                + "fluid handler=" + fluidCompat
                );

                setCanonical(level, placementPos, WaterVolumeChunk.UNITS_PER_BLOCK / 2);
                boolean rejectedPlacement = ((BucketItem) Items.WATER_BUCKET).emptyContents(
                        null,
                        level,
                        placementPos,
                        null,
                        new ItemStack(Items.WATER_BUCKET)
                );
                helper.assertTrue(
                        !rejectedPlacement
                                && WildernessWaterAuthority.getWaterAmount(level, placementPos)
                                == WaterVolumeChunk.UNITS_PER_BLOCK / 2,
                        "Disabled compatibility consumed a bucket into existing finite volume; "
                                + "fluid handler=" + fluidCompat
                );

                level.setBlock(vanillaPos, Blocks.WATER.defaultBlockState(), 3);
                BlockState vanillaSource = level.getBlockState(vanillaPos);
                ItemStack vanillaPickup = ((BucketPickup) vanillaSource.getBlock()).pickupBlock(
                        null,
                        level,
                        vanillaPos,
                        vanillaSource
                );
                helper.assertTrue(
                        vanillaPickup.is(Items.WATER_BUCKET)
                                && level.getBlockState(vanillaPos).isAir(),
                        "Disabled compatibility unexpectedly changed unowned vanilla water; "
                                + "fluid handler=" + fluidCompat
                );
            }
        } finally {
            WaterSimulationConfig.ENABLE_VANILLA_BUCKET_COMPAT.set(previousBucketCompat);
            WaterSimulationConfig.ENABLE_FLUID_HANDLER_COMPAT.set(previousFluidCompat);
        }
        helper.succeed();
    }

    private static void setCanonical(ServerLevel level, BlockPos position, int volumeUnits) {
        level.setBlock(position, Blocks.AIR.defaultBlockState(), 3);
        CanonicalWater.set(
                level,
                position,
                WaterVolumeChunk.WaterCell.still(volumeUnits, PROJECTED_FLAGS),
                true,
                false
        );
    }

    private static BlockSource placeDispenser(
            ServerLevel level,
            BlockPos position,
            Direction direction
    ) {
        BlockState state = Blocks.DISPENSER.defaultBlockState()
                .setValue(DispenserBlock.FACING, direction);
        level.setBlock(position, state, 3);
        DispenserBlockEntity blockEntity = (DispenserBlockEntity) level.getBlockEntity(position);
        return new BlockSource(level, position, state, blockEntity);
    }
}
