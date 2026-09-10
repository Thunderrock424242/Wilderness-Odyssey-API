package com.thunder.wildernessodysseyapi.watersystem.water.fluid;

import com.thunder.wildernessodysseyapi.core.ModConstants;
import com.thunder.wildernessodysseyapi.watersystem.water.config.WaterSimulationConfig;
import com.thunder.wildernessodysseyapi.watersystem.water.config.WildernessWaterRules;
import com.thunder.wildernessodysseyapi.watersystem.water.hydrology.TemporaryFloodManager;
import com.thunder.wildernessodysseyapi.watersystem.water.hydrology.TemporaryFloodSavedData;
import com.thunder.wildernessodysseyapi.watersystem.water.sph.SPHSimulationManager;
import com.thunder.wildernessodysseyapi.watersystem.water.volume.CanonicalWater;
import com.thunder.wildernessodysseyapi.watersystem.water.volume.WaterVolumeChunk;
import com.thunder.wildernessodysseyapi.watersystem.water.volume.WildernessWaterAuthority;
import com.thunder.wildernessodysseyapi.watersystem.water.wave.WaterBodyClassifier;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.BucketItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.PointedDripstoneBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.util.BlockSnapshot;
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

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.WeakHashMap;

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
    static final boolean ALLOW_SOURCE_CONVERSION = false;

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
     * finite storage behavior, and bucket sounds. Compatibility with tag-aware
     * systems is supplied by data tags, not by registering anything inside the
     * {@code minecraft} namespace.</p>
     */
    public static final DeferredHolder<FluidType, FluidType> WILDERNESS_WATER_TYPE = FLUID_TYPES.register(
            "wilderness_water",
            () -> new FluidType(FluidType.Properties.create()
                    .descriptionId("block.wildernessodysseyapi.wilderness_water")
                    .fallDistanceModifier(0.0F)
                    .canExtinguish(true)
                    .canConvertToSource(ALLOW_SOURCE_CONVERSION)
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
                    return ALLOW_SOURCE_CONVERSION;
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

    private static final Direction[] HORIZONTAL_DIRECTIONS = {
            Direction.NORTH,
            Direction.SOUTH,
            Direction.WEST,
            Direction.EAST
    };
    private static final int MOBILE_POUR_MIN_UNITS = WaterVolumeChunk.UNITS_PER_BLOCK / 4;
    private static final int MOBILE_POUR_MAX_UNITS = WaterVolumeChunk.UNITS_PER_BLOCK / 2;
    private static final float MAX_FALL_SPEED = -8.0f;
    private static final float MAX_SIDE_SPEED = 4.8f;
    private static final double MOMENTUM_DRAG_PER_SECOND = 2.5;
    private static final int MAX_FLOW_CLOCK_ENTRIES = 131_072;
    private static final Map<ServerLevel, LinkedHashMap<Long, Long>> FLOW_EVALUATION_TICKS =
            new WeakHashMap<>();
    private static final Map<ServerLevel, long[]> FLOW_MEASUREMENTS = new WeakHashMap<>();

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
        if (!WildernessWaterRules.isEnabled(level)) {
            return;
        }

        long started = System.nanoTime();
        long[] measurements = FLOW_MEASUREMENTS.computeIfAbsent(level, ignored -> new long[3]);
        Arrays.fill(measurements, 0);
        int maxCells = WaterSimulationConfig.localFlowCellsPerTick();
        java.util.Set<Long> evaluated = new java.util.HashSet<>();
        java.util.List<BlockPos> deferred = new java.util.ArrayList<>();
        for (int processed = 0; processed < maxCells; processed++) {
            BlockPos pos = CanonicalWater.pollActive(level);
            if (pos == null) {
                break;
            }
            if (evaluated.add(pos.asLong())) {
                measurements[0]++;
                tickCell(level, pos);
            }
            else deferred.add(pos);
        }
        for (BlockPos pos : deferred) CanonicalWater.schedule(level, pos);
        measurements[2] = System.nanoTime() - started;
    }

    /** Latest bounded local-flow pass; volume includes accepted canonical-to-SPH handoffs. */
    public static String diagnostics(ServerLevel level) {
        long[] measurement = FLOW_MEASUREMENTS.get(level);
        return measurement == null ? "canonical flow has not run" : "canonical cells processed=" + measurement[0]
                + ", canonical units moved=" + measurement[1] + ", solver microseconds=" + measurement[2] / 1000;
    }

    /** Releases only scheduling clocks and measurements, never persisted water. */
    public static void clearLevel(ServerLevel level) {
        FLOW_EVALUATION_TICKS.remove(level);
        FLOW_MEASUREMENTS.remove(level);
    }

    /**
     * Wakes nearby canonical water after terrain changes.
     *
     * <p>Placed buckets enter finite flow immediately, while settled canonical
     * water may later sleep after pressure equalizes. A block edit can create a
     * new outlet, so only already-tracked nearby cells are queued; normal block
     * edits never trigger chunk-wide water imports.</p>
     */
    @SubscribeEvent
    public static void onBlockBroken(BlockEvent.BreakEvent event) {
        if (event.getLevel() instanceof ServerLevel level) {
            WaterBodyClassifier.invalidate(level, event.getPos());
            if (WildernessWaterRules.isEnabled(level)) {
                wakeTrackedWaterAround(level, event.getPos());
            }
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
            WaterBodyClassifier.invalidate(level, event.getPos());
            if (WildernessWaterRules.isEnabled(level)) {
                displaceWaterForPlacedBlocks(level, event);
                wakeTrackedWaterAround(level, event.getPos());
            }
        }
    }

    static void tickCell(ServerLevel level, BlockPos pos) {
        WaterVolumeChunk.WaterCell current = CanonicalWater.getOrImport(level, pos);
        if (current.volumeUnits() <= 0 || current.imported() || current.sleeping()) {
            forgetFlowTime(level, pos);
            return;
        }

        double dtSeconds = elapsedFlowSeconds(level, pos);
        if (dtSeconds <= 0) { CanonicalWater.schedule(level, pos); return; }
        float damping = (float) Math.exp(-MOMENTUM_DRAG_PER_SECOND * dtSeconds);

        int remaining = current.volumeUnits();
        int transferredUnits = 0;
        boolean downwardTransferPending = false;

        // Gravity gets first claim on unsettled volume. A falling sheet can
        // either fill canonical capacity below or hand high-energy water to SPH
        // so waterfalls/pours are rendered as mobile water before settling.
        BlockPos below = pos.below();
        if (canOccupy(level, below)) {
            WaterVolumeChunk.WaterCell target = CanonicalWater.getOrImport(level, below);
            boolean ownershipBlocked = sourceCannotMixFloodOwnership(current, target);
            int transfer = FiniteWaterFlowPlanner.verticalTransfer(
                    remaining,
                    ownershipBlocked ? WaterVolumeChunk.UNITS_PER_BLOCK : target.volumeUnits(),
                    dtSeconds
            );
            if (transfer > 0) {
                int mobileTransfer = maybeCreateMobilePour(level, pos, current, target, transfer);
                if (mobileTransfer > 0) {
                    remaining -= mobileTransfer;
                    transferredUnits += mobileTransfer;
                    downwardTransferPending = mobileTransfer < transfer;
                } else {
                    int accepted = addTargetVolume(
                            level,
                            pos,
                            below,
                            transfer,
                            current.velocityX() * 0.45f,
                            Math.max(MAX_FALL_SPEED, current.velocityY()
                                    - (float) (FiniteWaterFlowPlanner.GRAVITY_METERS_PER_SECOND_SQUARED
                                    * dtSeconds)),
                            current.velocityZ() * 0.45f,
                            current
                    );
                    assert accepted >= 0 && accepted <= transfer
                            : "Canonical destination accepted an invalid gravity transfer amount";
                    remaining -= accepted;
                    transferredUnits += accepted;
                    downwardTransferPending = accepted < transfer;
                }
            }
        }

        if (!downwardTransferPending && remaining > FiniteWaterFlowPlanner.MIN_FLOW_UNITS) {
            int lateralMoved = flowSideways(level, pos, current, remaining, dtSeconds);
            remaining -= lateralMoved;
            transferredUnits += lateralMoved;
        }

        assert current.volumeUnits() == remaining + transferredUnits
                : "Finite-water transfer violated source conservation at " + pos;
        if (transferredUnits > 0) {
            commitSource(level, pos, current, remaining, damping);
            long[] measurements = FLOW_MEASUREMENTS.get(level);
            if (measurements != null) measurements[1] += transferredUnits;
        } else if (!shouldSleep(current)) {
            // A disturbed cell that cannot currently move should calm down
            // instead of carrying stale velocity forever.
            CanonicalWater.set(level, pos, current.withFlowState(
                    current.volumeUnits(),
                    current.velocityX() * damping,
                    current.velocityY() * damping,
                    current.velocityZ() * damping
            ).withAddedFlags(WaterVolumeChunk.FLAG_COMPATIBILITY_PROJECTED), true);
        } else {
            CanonicalWater.set(level, pos, current.withFlowState(
                    current.volumeUnits(),
                    0.0f,
                    0.0f,
                    0.0f
            ).withAddedFlags(WaterVolumeChunk.FLAG_COMPATIBILITY_PROJECTED
                    | WaterVolumeChunk.FLAG_SLEEPING), true, false);
            forgetFlowTime(level, pos);
        }
    }

    private static int flowSideways(
            ServerLevel level,
            BlockPos sourcePos,
            WaterVolumeChunk.WaterCell source,
            int sourceVolume,
            double dtSeconds
    ) {
        BlockPos[] positions = new BlockPos[HORIZONTAL_DIRECTIONS.length];
        int[] targetVolumes = new int[HORIZONTAL_DIRECTIONS.length];
        double[] targetBeds = new double[HORIZONTAL_DIRECTIONS.length];
        double[] openingFractions = new double[HORIZONTAL_DIRECTIONS.length];
        double sourceBed = bedElevation(level, sourcePos);
        Arrays.fill(targetVolumes, FiniteWaterFlowPlanner.BLOCKED_TARGET);
        for (int index = 0; index < HORIZONTAL_DIRECTIONS.length; index++) {
            Direction direction = HORIZONTAL_DIRECTIONS[index];
            BlockPos neighbourPos = sourcePos.relative(direction);
            if (!canOccupy(level, neighbourPos)) {
                continue;
            }
            WaterVolumeChunk.WaterCell neighbour = CanonicalWater.getOrImport(level, neighbourPos);
            if (sourceCannotMixFloodOwnership(source, neighbour)) {
                continue;
            }
            positions[index] = neighbourPos;
            targetVolumes[index] = neighbour.volumeUnits();
            targetBeds[index] = bedElevation(level, neighbourPos);
            openingFractions[index] = connectionOpening(level, sourcePos, neighbourPos);
        }

        FiniteWaterFlowPlanner.LateralPlan plan =
                FiniteWaterFlowPlanner.planHydraulic(
                        sourceBed,
                        sourceVolume,
                        targetBeds,
                        targetVolumes,
                        openingFractions,
                        dtSeconds
                );
        int moved = 0;
        int[] transfers = plan.transfers();
        for (int index = 0; index < transfers.length; index++) {
            int requested = transfers[index];
            BlockPos targetPos = positions[index];
            if (requested <= 0 || targetPos == null) {
                continue;
            }
            Direction direction = HORIZONTAL_DIRECTIONS[index];
            double sourceHead = sourceBed
                    + sourceVolume / (double) WaterVolumeChunk.UNITS_PER_BLOCK;
            double targetHead = targetBeds[index]
                    + targetVolumes[index] / (double) WaterVolumeChunk.UNITS_PER_BLOCK;
            double gradient = Math.max(0.0, sourceHead - targetHead);
            float velocityX = FiniteWaterFlowPlanner.velocityAfterHeadGradient(
                    source.velocityX(),
                    direction.getStepX() * gradient,
                    dtSeconds,
                    MOMENTUM_DRAG_PER_SECOND,
                    MAX_SIDE_SPEED
            );
            float velocityZ = FiniteWaterFlowPlanner.velocityAfterHeadGradient(
                    source.velocityZ(),
                    direction.getStepZ() * gradient,
                    dtSeconds,
                    MOMENTUM_DRAG_PER_SECOND,
                    MAX_SIDE_SPEED
            );
            int accepted = addTargetVolume(
                    level,
                    sourcePos,
                    targetPos,
                    requested,
                    velocityX,
                    source.velocityY() * 0.20f,
                    velocityZ,
                    source
            );
            assert accepted >= 0 && accepted <= requested
                    : "Canonical destination accepted an invalid transfer amount";
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
        // SPH currently owns exact canonical units but not flood-ledger provenance.
        // Keep flood-owned water canonical until that ownership token is represented by SPH.
        if (source.temporaryFlood()
                || target.volumeUnits() > 0
                || transfer <= 0 || source.volumeUnits() < MOBILE_POUR_MIN_UNITS
                || source.velocityY() > -1.0f) {
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
                Math.max(MAX_FALL_SPEED, source.velocityY() - 1.0f),
                source.velocityZ() * 0.35f
        );
        return created ? mobileVolume : 0;
    }

    private static int addTargetVolume(
            ServerLevel level,
            BlockPos sourcePos,
            BlockPos targetPos,
            int transfer,
            float velocityX,
            float velocityY,
            float velocityZ,
            WaterVolumeChunk.WaterCell source
    ) {
        // Commit the destination first. Its accepted amount is authoritative,
        // so a changed or non-replaceable target can never make volume vanish.
        return CanonicalWater.addTransferredVolume(level, sourcePos, targetPos, source,
                transfer, velocityX, velocityY, velocityZ);
    }

    private static void commitSource(
            ServerLevel level,
            BlockPos sourcePos,
            WaterVolumeChunk.WaterCell source,
            int remaining,
            float damping
    ) {
        BlockState originalFloodState = null;
        TemporaryFloodSavedData floodLedger = null;
        if (remaining <= 0 && source.temporaryFlood()) {
            floodLedger = TemporaryFloodSavedData.get(level);
            originalFloodState = floodLedger.originalState(sourcePos.asLong());
        }
        CanonicalWater.set(level, sourcePos, remaining <= 0
                ? WaterVolumeChunk.WaterCell.EMPTY
                : source.withFlowState(
                        remaining,
                        source.velocityX() * damping,
                        source.velocityY() * damping,
                        source.velocityZ() * damping
                ).withAddedFlags(WaterVolumeChunk.FLAG_COMPATIBILITY_PROJECTED)
                        .withoutFlags(WaterVolumeChunk.FLAG_SLEEPING), true);
        if (remaining <= 0 && floodLedger != null && floodLedger.forget(sourcePos.asLong())) {
            TemporaryFloodManager.restoreOriginalState(level, sourcePos, originalFloodState);
        }
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

    private static boolean sourceCannotMixFloodOwnership(
            WaterVolumeChunk.WaterCell source,
            WaterVolumeChunk.WaterCell target
    ) {
        return target.volumeUnits() > 0 && source.temporaryFlood() != target.temporaryFlood();
    }

    private static double elapsedFlowSeconds(ServerLevel level, BlockPos pos) {
        long gameTime = level.getGameTime();
        LinkedHashMap<Long, Long> levelTicks = FLOW_EVALUATION_TICKS.computeIfAbsent(
                level,
                ignored -> new LinkedHashMap<>(256, 0.75f, true)
        );
        Long previousTick = levelTicks.put(pos.asLong(), gameTime);
        while (levelTicks.size() > MAX_FLOW_CLOCK_ENTRIES) {
            var iterator = levelTicks.keySet().iterator();
            iterator.next();
            iterator.remove();
        }
        return FiniteWaterFlowPlanner.elapsedSeconds(previousTick, gameTime);
    }

    private static void forgetFlowTime(ServerLevel level, BlockPos pos) {
        LinkedHashMap<Long, Long> levelTicks = FLOW_EVALUATION_TICKS.get(level);
        if (levelTicks != null) {
            levelTicks.remove(pos.asLong());
            if (levelTicks.isEmpty()) {
                FLOW_EVALUATION_TICKS.remove(level);
            }
        }
    }

    private static double bedElevation(ServerLevel level, BlockPos pos) {
        // Canonical depth occupies this voxel, not the collision top in the
        // block below. A bounded connected column adds hydrostatic pressure.
        double columnHead = pos.getY();
        if (WildernessWaterAuthority.getWaterAmount(level, pos) < WaterVolumeChunk.UNITS_PER_BLOCK) return columnHead;
        for (int offset = 1; offset <= 8; offset++) {
            BlockPos above = pos.above(offset);
            if (level.isOutsideBuildHeight(above) || !level.hasChunkAt(above)) break;
            int units = WildernessWaterAuthority.getWaterAmount(level, above);
            if (units <= 0) break;
            columnHead += units / (double) WaterVolumeChunk.UNITS_PER_BLOCK;
            if (units < WaterVolumeChunk.UNITS_PER_BLOCK) break;
        }
        return columnHead;
    }

    private static double connectionOpening(ServerLevel level, BlockPos source, BlockPos target) {
        var sourceShape = level.getBlockState(source).getCollisionShape(level, source);
        var targetShape = level.getBlockState(target).getCollisionShape(level, target);
        if (sourceShape.isEmpty() && targetShape.isEmpty()) {
            return 1.0;
        }
        // Waterlogged or narrow collision hosts get a deliberately conservative opening.
        boolean waterPresent = !level.getFluidState(source).isEmpty()
                || !level.getFluidState(target).isEmpty();
        return waterPresent ? 0.25 : 0.0;
    }

    private static void displaceWaterForPlacedBlocks(ServerLevel level, BlockEvent.EntityPlaceEvent event) {
        if (event instanceof BlockEvent.EntityMultiPlaceEvent multiPlaceEvent) {
            for (BlockSnapshot snapshot : multiPlaceEvent.getReplacedBlockSnapshots()) {
                displaceWaterForPlacedSnapshot(level, snapshot);
            }
            return;
        }
        displaceWaterForPlacedSnapshot(level, event.getBlockSnapshot());
    }

    private static void displaceWaterForPlacedSnapshot(ServerLevel level, BlockSnapshot snapshot) {
        BlockPos pos = snapshot.getPos();
        if (level.isOutsideBuildHeight(pos) || !level.hasChunkAt(pos)) {
            return;
        }

        // NeoForge snapshots preserve the replaced state. The live state is the
        // placed block, so the authority can conserve water without overwriting
        // the player's newly placed solid.
        int moved = CanonicalWater.displaceForSolidPlacement(
                level,
                pos,
                snapshot.getState(),
                level.getBlockState(pos)
        );
        if (moved > 0) {
            wakeTrackedWaterAround(level, pos);
        }
    }

    /** Notifies existing water owners after a server environmental terrain edit. */
    public static void notifyTerrainChanged(ServerLevel level, BlockPos pos) {
        com.thunder.wildernessodysseyapi.watersystem.water.surface.WaterDepthSampler.invalidate(level, pos);
        CanonicalWater.materializeGeneratedNeighborhood(level, pos);
        WaterBodyClassifier.invalidate(level, pos);
        com.thunder.wildernessodysseyapi.watersystem.ocean.shore.ShorelineWaterManager.get().invalidate(level, pos);
        com.thunder.wildernessodysseyapi.watersystem.water.hydrology.WatershedSavedData.get(level)
                .refreshTerrain(level, pos);
        wakeTrackedWaterAround(level, pos);
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

}
