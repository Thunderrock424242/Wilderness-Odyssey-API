package com.thunder.wildernessodysseyapi.watersystem.water.fluid;

import com.thunder.wildernessodysseyapi.core.ModConstants;
import com.thunder.wildernessodysseyapi.watersystem.water.config.WaterSimulationConfig;
import com.thunder.wildernessodysseyapi.watersystem.water.sph.SPHSimulationManager;
import com.thunder.wildernessodysseyapi.watersystem.water.volume.CanonicalWater;
import com.thunder.wildernessodysseyapi.watersystem.water.volume.WaterVolumeChunk;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.BucketItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.PointedDripstoneBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.SoundActions;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;
import net.neoforged.neoforge.fluids.BaseFlowingFluid;
import net.neoforged.neoforge.fluids.FluidType;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Advances disturbed cells in the canonical finite-volume water state.
 *
 * <p>World-generation water is lazily imported as a stable reservoir and does
 * not consume tick budget until gameplay disturbs it. Player water and derived
 * flow use a bounded active queue, conserve fixed-point volume, prefer gravity,
 * and project results back to vanilla blocks for compatibility.</p>
 */
@EventBusSubscriber(modid = ModConstants.MOD_ID)
public final class WildernessFluidRegistry {

    public static final DeferredRegister<FluidType> FLUID_TYPES =
            DeferredRegister.create(NeoForgeRegistries.Keys.FLUID_TYPES, ModConstants.MOD_ID);
    public static final DeferredRegister<Fluid> FLUIDS =
            DeferredRegister.create(Registries.FLUID, ModConstants.MOD_ID);
    public static final DeferredRegister.Blocks BLOCKS =
            DeferredRegister.createBlocks(ModConstants.MOD_ID);
    public static final DeferredRegister.Items ITEMS =
            DeferredRegister.createItems(ModConstants.MOD_ID);

    /**
     * Fluid type used by the namespaced Wilderness water registry entries.
     *
     * <p>The type behaves like vanilla water for swimming, boating, hydration,
     * source conversion, and bucket sounds. Compatibility with tag-aware
     * systems is supplied by data tags, not by registering anything inside the
     * {@code minecraft} namespace.</p>
     */
    public static final DeferredHolder<FluidType, FluidType> WILDERNESS_WATER_TYPE = FLUID_TYPES.register(
            "wilderness_water",
            () -> new FluidType(FluidType.Properties.create()
                    .descriptionId("block.wildernessodysseyapi.wilderness_water")
                    .fallDistanceModifier(0.0F)
                    .canExtinguish(true)
                    .canConvertToSource(true)
                    .supportsBoating(true)
                    .canHydrate(true)
                    .sound(SoundActions.BUCKET_FILL, SoundEvents.BUCKET_FILL)
                    .sound(SoundActions.BUCKET_EMPTY, SoundEvents.BUCKET_EMPTY)
                    .sound(SoundActions.FLUID_VAPORIZE, SoundEvents.FIRE_EXTINGUISH)
                    .addDripstoneDripping(
                            PointedDripstoneBlock.WATER_TRANSFER_PROBABILITY_PER_RANDOM_TICK,
                            ParticleTypes.DRIPPING_DRIPSTONE_WATER,
                            Blocks.WATER_CAULDRON,
                            SoundEvents.POINTED_DRIPSTONE_DRIP_WATER_INTO_CAULDRON
                    )) {
                @Override
                public boolean canConvertToSource(FluidState state, LevelReader reader, BlockPos pos) {
                    if (reader instanceof ServerLevel level) {
                        return level.getGameRules().getBoolean(GameRules.RULE_WATER_SOURCE_CONVERSION);
                    }
                    return super.canConvertToSource(state, reader, pos);
                }
            }
    );

    public static final DeferredHolder<Fluid, BaseFlowingFluid.Source> WILDERNESS_WATER = FLUIDS.register(
            "wilderness_water",
            () -> new BaseFlowingFluid.Source(wildernessWaterProperties())
    );
    public static final DeferredHolder<Fluid, BaseFlowingFluid.Flowing> FLOWING_WILDERNESS_WATER = FLUIDS.register(
            "flowing_wilderness_water",
            () -> new BaseFlowingFluid.Flowing(wildernessWaterProperties())
    );
    public static final DeferredBlock<LiquidBlock> WILDERNESS_WATER_BLOCK = BLOCKS.register(
            "wilderness_water_block",
            () -> new LiquidBlock(WILDERNESS_WATER.get(), BlockBehaviour.Properties.of()
                    .mapColor(MapColor.WATER)
                    .replaceable()
                    .noCollission()
                    .strength(100.0F)
                    .pushReaction(PushReaction.DESTROY)
                    .noLootTable()
                    .liquid()
                    .sound(SoundType.EMPTY))
    );
    public static final DeferredItem<BucketItem> WILDERNESS_WATER_BUCKET = ITEMS.register(
            "wilderness_water_bucket",
            () -> new BucketItem(WILDERNESS_WATER.get(),
                    new Item.Properties().craftRemainder(Items.BUCKET).stacksTo(1))
    );

    private static final int MIN_FLOW_UNITS = WaterVolumeChunk.UNITS_PER_BLOCK / 64;
    private static final int MIN_LATERAL_DIFFERENCE_UNITS = WaterVolumeChunk.UNITS_PER_BLOCK / 16;
    private static final int MAX_VERTICAL_TRANSFER_UNITS = WaterVolumeChunk.UNITS_PER_BLOCK * 3 / 4;
    private static final int MAX_LATERAL_TRANSFER_UNITS = WaterVolumeChunk.UNITS_PER_BLOCK / 4;
    private static final int MOBILE_POUR_MIN_UNITS = WaterVolumeChunk.UNITS_PER_BLOCK / 4;
    private static final int MOBILE_POUR_MAX_UNITS = WaterVolumeChunk.UNITS_PER_BLOCK / 2;
    private static final float FALL_SPEED = -4.8f;
    private static final float SIDE_FLOW_SPEED = 0.85f;
    private static final float SOURCE_VELOCITY_DAMPING = 0.62f;

    private WildernessFluidRegistry() {
    }

    /**
     * Registers the namespaced Wilderness water and its finite-volume runtime.
     *
     * <p>The source/flowing fluids give the water system a real registry target
     * that can be tagged as water without taking over the {@code minecraft}
     * namespace. The ticker below remains the server-side owner of disturbed
     * canonical volume.</p>
     */
    public static void register(IEventBus modEventBus) {
        FLUID_TYPES.register(modEventBus);
        FLUIDS.register(modEventBus);
        BLOCKS.register(modEventBus);
        ITEMS.register(modEventBus);
    }

    private static BaseFlowingFluid.Properties wildernessWaterProperties() {
        return new BaseFlowingFluid.Properties(
                WILDERNESS_WATER_TYPE::get,
                WILDERNESS_WATER::get,
                FLOWING_WILDERNESS_WATER::get
        ).bucket(WILDERNESS_WATER_BUCKET::get)
                .block(WILDERNESS_WATER_BLOCK::get)
                .slopeFindDistance(4)
                .levelDecreasePerBlock(1)
                .tickRate(5)
                .explosionResistance(100.0F);
    }

    /** Processes a bounded number of disturbed canonical cells after each level tick. */
    @SubscribeEvent
    public static void onServerLevelTick(LevelTickEvent.Post event) {
        if (!(event.getLevel() instanceof ServerLevel level)) {
            return;
        }

        int maxCells = WaterSimulationConfig.localFlowCellsPerTick();
        for (int processed = 0; processed < maxCells; processed++) {
            BlockPos pos = CanonicalWater.pollActive(level);
            if (pos == null) {
                break;
            }
            tickCell(level, pos);
        }
    }

    /**
     * Wakes nearby canonical water after terrain changes.
     *
     * <p>Flat bucket water is allowed to sleep so it does not smear itself into
     * invisible film, but a later block break or placement can create a real
     * outlet. Only already-tracked cells are queued, which avoids turning normal
     * block edits into chunk-wide water imports.</p>
     */
    @SubscribeEvent
    public static void onBlockBroken(BlockEvent.BreakEvent event) {
        if (event.getLevel() instanceof ServerLevel level) {
            wakeTrackedWaterAround(level, event.getPos());
        }
    }

    /**
     * Wakes nearby canonical water after player or automation block placement.
     *
     * <p>This lets the authority re-check local pressure when a new block
     * dams, redirects, or exposes a small water feature.</p>
     */
    @SubscribeEvent
    public static void onBlockPlaced(BlockEvent.EntityPlaceEvent event) {
        if (event.getLevel() instanceof ServerLevel level) {
            wakeTrackedWaterAround(level, event.getPos());
        }
    }

    private static void tickCell(ServerLevel level, BlockPos pos) {
        WaterVolumeChunk.WaterCell current = CanonicalWater.getOrImport(level, pos);
        if (current.volumeUnits() <= 0 || current.imported() || current.sleeping()) {
            return;
        }

        int remaining = current.volumeUnits();
        boolean moved = false;

        // Gravity gets first claim on unsettled volume. A falling sheet can
        // either fill canonical capacity below or hand high-energy water to SPH
        // so waterfalls/pours are rendered as mobile water before settling.
        BlockPos below = pos.below();
        if (canOccupy(level, below)) {
            WaterVolumeChunk.WaterCell target = CanonicalWater.getOrImport(level, below);
            int capacity = WaterVolumeChunk.UNITS_PER_BLOCK - target.volumeUnits();
            int transfer = Math.min(remaining, Math.min(Math.max(0, capacity), MAX_VERTICAL_TRANSFER_UNITS));
            if (transfer > 0) {
                int mobileTransfer = maybeCreateMobilePour(level, pos, current, target, transfer);
                if (mobileTransfer > 0) {
                    remaining -= mobileTransfer;
                    moved = true;
                } else {
                    int accepted = addTargetVolume(
                            level,
                            below,
                            transfer,
                            current.velocityX() * 0.45f,
                            Math.min(FALL_SPEED, current.velocityY() - 1.2f),
                            current.velocityZ() * 0.45f
                    );
                    remaining -= accepted;
                    moved |= accepted > 0;
                }
            }
        }

        if (remaining > MIN_FLOW_UNITS) {
            int lateralMoved = flowSideways(level, pos, current, remaining);
            remaining -= lateralMoved;
            moved |= lateralMoved > 0;
        }

        if (moved) {
            commitSource(level, pos, current, remaining);
        } else if (!shouldSleep(current)) {
            // A disturbed cell that cannot currently move should calm down
            // instead of carrying stale velocity forever.
            CanonicalWater.set(level, pos, new WaterVolumeChunk.WaterCell(
                    current.volumeUnits(),
                    current.velocityX() * SOURCE_VELOCITY_DAMPING,
                    current.velocityY() * SOURCE_VELOCITY_DAMPING,
                    current.velocityZ() * SOURCE_VELOCITY_DAMPING,
                    WaterVolumeChunk.FLAG_COMPATIBILITY_PROJECTED,
                    current.temperatureMilliKelvin()
            ), true);
        } else {
            CanonicalWater.set(level, pos, new WaterVolumeChunk.WaterCell(
                    current.volumeUnits(),
                    0.0f,
                    0.0f,
                    0.0f,
                    WaterVolumeChunk.FLAG_COMPATIBILITY_PROJECTED | WaterVolumeChunk.FLAG_SLEEPING,
                    current.temperatureMilliKelvin()
            ), true, false);
        }
    }

    private static int flowSideways(
            ServerLevel level,
            BlockPos sourcePos,
            WaterVolumeChunk.WaterCell source,
            int sourceVolume
    ) {
        List<LateralCandidate> candidates = new ArrayList<>(4);
        for (Direction direction : Direction.Plane.HORIZONTAL) {
            BlockPos neighbourPos = sourcePos.relative(direction);
            if (!canOccupy(level, neighbourPos)) {
                continue;
            }
            WaterVolumeChunk.WaterCell neighbour = CanonicalWater.getOrImport(level, neighbourPos);
            int capacity = WaterVolumeChunk.UNITS_PER_BLOCK - neighbour.volumeUnits();
            int difference = sourceVolume - neighbour.volumeUnits();
            if (capacity > 0 && difference > MIN_LATERAL_DIFFERENCE_UNITS) {
                candidates.add(new LateralCandidate(direction, neighbourPos, capacity, difference));
            }
        }

        if (candidates.isEmpty()) {
            return 0;
        }
        candidates.sort(Comparator.comparingInt(LateralCandidate::difference).reversed());

        int remaining = sourceVolume;
        int moved = 0;
        for (LateralCandidate candidate : candidates) {
            if (remaining <= MIN_FLOW_UNITS) {
                break;
            }
            float gradient = Math.min(1.0f,
                    candidate.difference() / (float) WaterVolumeChunk.UNITS_PER_BLOCK);
            int requested = Math.min(
                    Math.min(candidate.capacity(), remaining),
                    Math.max(MIN_FLOW_UNITS, Math.min(MAX_LATERAL_TRANSFER_UNITS,
                            candidate.difference() / (candidates.size() + 1)))
            );
            if (requested <= 0) {
                continue;
            }

            float velocityX = source.velocityX() * 0.35f
                    + candidate.direction().getStepX() * SIDE_FLOW_SPEED * gradient;
            float velocityZ = source.velocityZ() * 0.35f
                    + candidate.direction().getStepZ() * SIDE_FLOW_SPEED * gradient;
            int accepted = addTargetVolume(
                    level,
                    candidate.pos(),
                    requested,
                    velocityX,
                    source.velocityY() * 0.20f,
                    velocityZ
            );
            remaining -= accepted;
            moved += accepted;
        }
        return moved;
    }

    private static int maybeCreateMobilePour(
            ServerLevel level,
            BlockPos sourcePos,
            WaterVolumeChunk.WaterCell source,
            WaterVolumeChunk.WaterCell target,
            int transfer
    ) {
        if (target.volumeUnits() > 0 || transfer < MOBILE_POUR_MIN_UNITS) {
            return 0;
        }
        int mobileVolume = Math.min(transfer, MOBILE_POUR_MAX_UNITS);
        boolean created = SPHSimulationManager.get().createCanonicalFlowSimulation(
                sourcePos.getX() + 0.5f,
                sourcePos.getY() + 0.35f,
                sourcePos.getZ() + 0.5f,
                level,
                mobileVolume,
                source.velocityX() * 0.35f,
                Math.min(FALL_SPEED, source.velocityY() - 1.0f),
                source.velocityZ() * 0.35f
        );
        return created ? mobileVolume : 0;
    }

    private static int addTargetVolume(
            ServerLevel level,
            BlockPos targetPos,
            int transfer,
            float velocityX,
            float velocityY,
            float velocityZ
    ) {
        // Commit the destination first. Its accepted amount is authoritative,
        // so a changed or non-replaceable target can never make volume vanish.
        return CanonicalWater.addVolume(
                level,
                targetPos,
                transfer,
                velocityX,
                velocityY,
                velocityZ
        );
    }

    private static void commitSource(
            ServerLevel level,
            BlockPos sourcePos,
            WaterVolumeChunk.WaterCell source,
            int remaining
    ) {
        CanonicalWater.set(level, sourcePos, remaining <= 0
                ? WaterVolumeChunk.WaterCell.EMPTY
                : new WaterVolumeChunk.WaterCell(
                        remaining,
                        source.velocityX() * SOURCE_VELOCITY_DAMPING,
                        source.velocityY() * SOURCE_VELOCITY_DAMPING,
                        source.velocityZ() * SOURCE_VELOCITY_DAMPING,
                        WaterVolumeChunk.FLAG_COMPATIBILITY_PROJECTED,
                        source.temperatureMilliKelvin()
                ), true);
    }

    private static float speedSquared(WaterVolumeChunk.WaterCell cell) {
        return cell.velocityX() * cell.velocityX()
                + cell.velocityY() * cell.velocityY()
                + cell.velocityZ() * cell.velocityZ();
    }

    private static boolean shouldSleep(WaterVolumeChunk.WaterCell cell) {
        float sleepSpeed = WaterSimulationConfig.localFlowSleepSpeed();
        return speedSquared(cell) <= sleepSpeed * sleepSpeed;
    }

    private static boolean canOccupy(ServerLevel level, BlockPos pos) {
        return CanonicalWater.canAcceptVolume(level, pos);
    }

    private static void wakeTrackedWaterAround(ServerLevel level, BlockPos pos) {
        scheduleIfTracked(level, pos);
        scheduleIfTracked(level, pos.below());
        scheduleIfTracked(level, pos.above());
        for (Direction direction : Direction.Plane.HORIZONTAL) {
            scheduleIfTracked(level, pos.relative(direction));
        }
    }

    private static void scheduleIfTracked(ServerLevel level, BlockPos pos) {
        if (!level.isOutsideBuildHeight(pos)
                && level.hasChunkAt(pos)
                && CanonicalWater.isTracked(level, pos)) {
            CanonicalWater.schedule(level, pos);
        }
    }

    private record LateralCandidate(Direction direction, BlockPos pos, int capacity, int difference) {
    }
}
