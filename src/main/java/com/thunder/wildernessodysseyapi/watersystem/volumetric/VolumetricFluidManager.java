package com.thunder.wildernessodysseyapi.watersystem.volumetric;

import com.thunder.wildernessodysseyapi.core.ModConstants;
import com.thunder.wildernessodysseyapi.watersystem.ocean.tide.TideAstronomy;
import com.thunder.wildernessodysseyapi.watersystem.ocean.tide.TideManager;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BiomeTags;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerStartingEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Sparse 3D water simulation that augments vanilla water with pressure and velocity.
 */
@EventBusSubscriber(modid = ModConstants.MOD_ID)
public final class VolumetricFluidManager {
    private static final Direction[] LATERALS = {Direction.NORTH, Direction.SOUTH, Direction.EAST, Direction.WEST};
    private static final Map<ResourceKey<Level>, Map<SimulatedFluid, FluidGrid>> GRIDS = new ConcurrentHashMap<>();
    private static VolumetricFluidConfig.Values cachedConfig = VolumetricFluidConfig.defaultValues();

    private VolumetricFluidManager() {
    }

    public static void reloadConfig() {
        cachedConfig = loadConfigWithFallback();
    }

    public static boolean shouldReplaceVanillaWaterEngine() {
        return cachedConfig.enabled() && cachedConfig.replaceVanillaWaterEngine();
    }

    public static boolean shouldReplaceVanillaLavaEngine() {
        return cachedConfig.enabled() && cachedConfig.enableLava() && cachedConfig.replaceVanillaLavaEngine();
    }

    public static boolean isLavaSimulationEnabled() {
        return cachedConfig.enabled() && cachedConfig.enableLava();
    }

    public static String activePreset() {
        return cachedConfig.preset();
    }

    public static void ingestVanillaWaterTick(ServerLevel level, BlockPos pos, double normalizedVolume) {
        if (!cachedConfig.enabled()) {
            return;
        }
        FluidGrid grid = getGrid(level, SimulatedFluid.WATER);
        FluidCell cell = grid.cells.computeIfAbsent(pos.asLong(), ignored -> new FluidCell());
        cell.volume = Math.max(cell.volume, Math.max(cachedConfig.minCellVolume(), normalizedVolume));
    }

    public static void ingestVanillaLavaTick(ServerLevel level, BlockPos pos, double normalizedVolume) {
        if (!cachedConfig.enabled() || !cachedConfig.enableLava()) {
            return;
        }
        FluidGrid grid = getGrid(level, SimulatedFluid.LAVA);
        FluidCell cell = grid.cells.computeIfAbsent(pos.asLong(), ignored -> new FluidCell());
        cell.volume = Math.max(cell.volume, Math.max(cachedConfig.minCellVolume(), normalizedVolume));
    }

    @SubscribeEvent
    public static void onServerStarting(ServerStartingEvent event) {
        GRIDS.clear();
        cachedConfig = loadConfigWithFallback();
    }

    @SubscribeEvent
    public static void onServerStopping(ServerStoppingEvent event) {
        GRIDS.clear();
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        if (!event.hasTime() || !cachedConfig.enabled()) {
            return;
        }

        MinecraftServer server = event.getServer();
        ServerLevel overworld = server.overworld();
        if (overworld == null) {
            return;
        }

        long gameTime = overworld.getGameTime();
        if (gameTime % Math.max(1, cachedConfig.tickInterval()) != 0L) {
            return;
        }

        for (ServerLevel level : server.getAllLevels()) {
            if (level.players().isEmpty()) {
                continue;
            }
            stepFluid(level, SimulatedFluid.WATER, cachedConfig);
            if (cachedConfig.enableLava()) {
                stepFluid(level, SimulatedFluid.LAVA, cachedConfig);
            }
        }
    }

    public static SimulationSnapshot getSnapshot(ServerLevel level, SimulatedFluid fluidType) {
        FluidGrid grid = getGrid(level, fluidType);
        double totalVolume = 0.0D;
        double totalSpeed = 0.0D;
        for (FluidCell cell : grid.cells.values()) {
            totalVolume += cell.volume;
            totalSpeed += Math.sqrt(cell.vx * cell.vx + cell.vy * cell.vy + cell.vz * cell.vz);
        }
        double avgSpeed = grid.cells.isEmpty() ? 0.0D : totalSpeed / grid.cells.size();
        return new SimulationSnapshot(grid.cells.size(), grid.controlledBlocks.size(), totalVolume, avgSpeed);
    }

    /**
     * Builds a compact list of fluid-surface samples that a client mesh renderer can consume.
     * One sample is emitted per X/Z column using the highest active cell.
     */
    public static List<SurfaceSample> sampleSurface(ServerLevel level,
                                                    SimulatedFluid fluidType,
                                                    BlockPos center,
                                                    int radius,
                                                    int maxSamples) {
        FluidGrid grid = getGrid(level, fluidType);
        if (grid.cells.isEmpty() || maxSamples <= 0 || radius <= 0) {
            return List.of();
        }

        int minX = center.getX() - radius;
        int maxX = center.getX() + radius;
        int minZ = center.getZ() - radius;
        int maxZ = center.getZ() + radius;
        Map<Long, SurfaceSample> byColumn = new HashMap<>();
        TideManager.TideSnapshot tideSnapshot = TideManager.snapshot(level);
        double moonPhaseFactor = fluidType == SimulatedFluid.WATER ? TideAstronomy.getMoonPhaseAmplitudeFactor(level) : 1.0D;

        for (Map.Entry<Long, FluidCell> entry : grid.cells.entrySet()) {
            BlockPos pos = BlockPos.of(entry.getKey());
            if (pos.getX() < minX || pos.getX() > maxX || pos.getZ() < minZ || pos.getZ() > maxZ) {
                continue;
            }
            BlockPos abovePos = pos.above();
            if (abovePos.getY() < level.getMaxBuildHeight()
                    && level.getFluidState(abovePos).getType() == fluidType.fluid()) {
                // Skip submerged cells; only emit columns where this cell is the exposed fluid surface.
                continue;
            }
            FluidCell cell = entry.getValue();
            if (cell.volume <= cachedConfig.minCellVolume()) {
                continue;
            }

            long columnKey = BlockPos.asLong(pos.getX(), 0, pos.getZ());
            double surfaceY = pos.getY() + Math.max(0.0D, Math.min(1.0D, cell.volume));
            double shorelineFactor = fluidType == SimulatedFluid.WATER ? computeShorelineFactor(level, pos) : 0.0D;
            double tideOffset = 0.0D;
            if (fluidType == SimulatedFluid.WATER) {
                tideOffset = tideSnapshot.normalizedHeight() * TideManager.getLocalAmplitude(level, pos);
            }
            SurfaceSample current = byColumn.get(columnKey);
            if (current == null || surfaceY > current.surfaceY()) {
                byColumn.put(columnKey, new SurfaceSample(
                        pos.getX(),
                        pos.getZ(),
                        surfaceY,
                        cell.volume,
                        shorelineFactor,
                        tideOffset,
                        moonPhaseFactor,
                        fluidType
                ));
            }
        }

        if (byColumn.isEmpty()) {
            return List.of();
        }

        List<SurfaceSample> samples = new ArrayList<>(byColumn.values());
        samples.sort((a, b) -> Double.compare(b.volume(), a.volume()));
        if (samples.size() > maxSamples) {
            return List.copyOf(samples.subList(0, maxSamples));
        }
        return List.copyOf(samples);
    }

    public static void clear(ServerLevel level, SimulatedFluid fluidType) {
        FluidGrid grid = getGridOrNull(level, fluidType);
        if (grid == null) {
            return;
        }
        for (Long packedPos : new ArrayList<>(grid.controlledBlocks)) {
            BlockPos pos = BlockPos.of(packedPos);
            if (level.getBlockState(pos).is(fluidType.block())) {
                level.setBlockAndUpdate(pos, Blocks.AIR.defaultBlockState());
            }
        }
        grid.cells.clear();
        grid.controlledBlocks.clear();
    }

    public static int seedFromExistingFluid(ServerLevel level, BlockPos center, int radius, SimulatedFluid fluidType) {
        FluidGrid grid = getGrid(level, fluidType);
        int injected = 0;
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        int minY = Math.max(level.getMinBuildHeight(), center.getY() - radius);
        int maxY = Math.min(level.getMaxBuildHeight() - 1, center.getY() + radius);

        for (int x = center.getX() - radius; x <= center.getX() + radius; x++) {
            for (int y = minY; y <= maxY; y++) {
                for (int z = center.getZ() - radius; z <= center.getZ() + radius; z++) {
                    cursor.set(x, y, z);
                    if (!level.isLoaded(cursor) || level.getFluidState(cursor).getType() != fluidType.fluid()) {
                        continue;
                    }
                    FluidCell cell = grid.cells.computeIfAbsent(cursor.asLong(), ignored -> new FluidCell());
                    cell.volume = Math.max(cell.volume, level.getFluidState(cursor).getAmount() / 8.0D);
                    injected++;
                }
            }
        }
        pruneCells(grid, cachedConfig.minCellVolume());
        return injected;
    }

    public static SurfaceSample sampleAtPosition(ServerLevel level, BlockPos pos, SimulatedFluid fluidType) {
        FluidGrid grid = getGrid(level, fluidType);
        FluidCell cell = grid.cells.get(pos.asLong());
        double volume = cell == null ? 0.0D : cell.volume;
        double shorelineFactor = fluidType == SimulatedFluid.WATER ? computeShorelineFactor(level, pos) : 0.0D;
        double tideOffset = 0.0D;
        double moonPhaseFactor = 1.0D;
        if (fluidType == SimulatedFluid.WATER) {
            TideManager.TideSnapshot tideSnapshot = TideManager.snapshot(level);
            tideOffset = tideSnapshot.normalizedHeight() * TideManager.getLocalAmplitude(level, pos);
            moonPhaseFactor = TideAstronomy.getMoonPhaseAmplitudeFactor(level);
        }
        double surfaceY = pos.getY() + Math.max(0.0D, Math.min(1.0D, volume));
        return new SurfaceSample(pos.getX(), pos.getZ(), surfaceY, volume, shorelineFactor, tideOffset, moonPhaseFactor, fluidType);
    }

    private static void stepFluid(ServerLevel level, SimulatedFluid fluidType, VolumetricFluidConfig.Values config) {
        FluidGrid grid = getGrid(level, fluidType);
        ingestFluidNearPlayers(level, grid, config, fluidType);
        stepSimulation(level, grid, config, fluidType);
        applyToWorld(level, grid, config, fluidType);
    }

    private static void ingestFluidNearPlayers(ServerLevel level, FluidGrid grid, VolumetricFluidConfig.Values config, SimulatedFluid fluidType) {
        int radius = config.playerSampleRadius();
        int step = Math.max(1, config.playerSampleStep());
        if (fluidType == SimulatedFluid.WATER && config.replaceVanillaWaterEngine()) {
            // Full-density sampling avoids checkerboard artifacts when vanilla ticks are cancelled.
            step = 1;
            radius = Math.max(radius, 24);
        } else if (fluidType == SimulatedFluid.LAVA && config.replaceVanillaLavaEngine()) {
            step = 1;
        }
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();

        for (ServerPlayer player : level.players()) {
            BlockPos origin = player.blockPosition();
            int minY = Math.max(level.getMinBuildHeight(), origin.getY() - radius);
            int maxY = Math.min(level.getMaxBuildHeight() - 1, origin.getY() + radius);
            for (int x = origin.getX() - radius; x <= origin.getX() + radius; x += step) {
                for (int y = minY; y <= maxY; y += step) {
                    for (int z = origin.getZ() - radius; z <= origin.getZ() + radius; z += step) {
                        cursor.set(x, y, z);
                        if (!level.isLoaded(cursor) || level.getFluidState(cursor).getType() != fluidType.fluid()) {
                            continue;
                        }
                        FluidCell cell = grid.cells.computeIfAbsent(cursor.asLong(), ignored -> new FluidCell());
                        cell.volume = Math.max(cell.volume, Math.max(0.125D, level.getFluidState(cursor).getAmount() / 8.0D));
                    }
                }
            }
        }
    }

    private static void stepSimulation(ServerLevel level, FluidGrid grid, VolumetricFluidConfig.Values config, SimulatedFluid fluidType) {
        if (grid.cells.isEmpty()) {
            return;
        }

        List<Long> keys = new ArrayList<>(grid.cells.keySet());
        int processed = 0;
        int activeRadiusSq = config.activeRadius() * config.activeRadius();

        for (int iteration = 0; iteration < config.pressureIterations(); iteration++) {
            Map<Long, Double> volumeDelta = new HashMap<>();
            for (Long packedPos : keys) {
                if (processed++ >= config.maxCellsPerStep()) {
                    break;
                }

                FluidCell cell = grid.cells.get(packedPos);
                if (cell == null || cell.volume <= config.minCellVolume()) {
                    continue;
                }

                BlockPos pos = BlockPos.of(packedPos);
                if (!isNearAnyPlayer(level, pos, activeRadiusSq)) {
                    continue;
                }

                double pressure = Math.max(0.0D, cell.volume - 0.5D);
                double remaining = cell.volume;

                // gravity/downward bias
                BlockPos below = pos.below();
                if (below.getY() >= level.getMinBuildHeight() && isFluidPassable(level, below, fluidType)) {
                    double down = Math.min(remaining, config.downwardTransfer() * (1.0D + pressure));
                    if (down > 0.0D) {
                        accumulate(volumeDelta, below.asLong(), down);
                        accumulate(volumeDelta, packedPos, -down);
                        cell.vy -= down;
                        remaining -= down;
                    }
                }

                // pressure equalization on lateral neighbors
                for (Direction direction : LATERALS) {
                    if (remaining <= config.minCellVolume()) {
                        break;
                    }
                    BlockPos sidePos = pos.relative(direction);
                    if (!isFluidPassable(level, sidePos, fluidType)) {
                        continue;
                    }

                    FluidCell neighbor = grid.cells.get(sidePos.asLong());
                    double neighborVolume = neighbor == null ? 0.0D : neighbor.volume;
                    double gradient = (cell.volume - neighborVolume);
                    if (gradient <= config.minCellVolume()) {
                        continue;
                    }

                    double momentumBias = 1.0D + directionalMomentum(cell, direction) * config.momentumBlend();
                    momentumBias = Math.max(0.15D, momentumBias);
                    double flow = Math.min(remaining,
                            Math.min(config.lateralTransfer() * momentumBias, gradient * config.pressureStrength() * momentumBias));
                    if (flow <= 0.0D) {
                        continue;
                    }

                    accumulate(volumeDelta, sidePos.asLong(), flow);
                    accumulate(volumeDelta, packedPos, -flow);

                    double directionalVelocity = flow * 0.6D;
                    switch (direction) {
                        case EAST -> cell.vx += directionalVelocity;
                        case WEST -> cell.vx -= directionalVelocity;
                        case SOUTH -> cell.vz += directionalVelocity;
                        case NORTH -> cell.vz -= directionalVelocity;
                        default -> {
                        }
                    }
                    remaining -= flow;
                }
            }

            applyVolumeDelta(grid, volumeDelta, config.minCellVolume());
            if (processed >= config.maxCellsPerStep()) {
                break;
            }
        }

        advectByVelocity(level, grid, config, fluidType);
        applyViscosityDiffusion(grid, config);
        applyVorticityConfinement(grid, config);
        applyTurbulence(level, grid, config);
        applyVelocityDamping(grid, config.inertiaDamping());
        pruneCells(grid, config.minCellVolume());
    }

    private static void advectByVelocity(ServerLevel level, FluidGrid grid, VolumetricFluidConfig.Values config, SimulatedFluid fluidType) {
        Map<Long, Double> advectedVolume = new HashMap<>();

        for (Map.Entry<Long, FluidCell> entry : new ArrayList<>(grid.cells.entrySet())) {
            long packedPos = entry.getKey();
            FluidCell cell = entry.getValue();
            if (cell.volume <= config.minCellVolume()) {
                continue;
            }

            int moveX = (int) Math.signum(cell.vx);
            int moveY = (int) Math.signum(cell.vy);
            int moveZ = (int) Math.signum(cell.vz);
            if (moveX == 0 && moveY == 0 && moveZ == 0) {
                continue;
            }

            BlockPos pos = BlockPos.of(packedPos);
            BlockPos target = pos.offset(moveX, moveY, moveZ);
            if (target.getY() < level.getMinBuildHeight() || target.getY() >= level.getMaxBuildHeight()) {
                continue;
            }
            if (!isFluidPassable(level, target, fluidType)) {
                continue;
            }

            double transfer = Math.min(cell.volume, config.advectionTransfer());
            if (transfer <= 0.0D) {
                continue;
            }

            accumulate(advectedVolume, packedPos, -transfer);
            accumulate(advectedVolume, target.asLong(), transfer);
        }

        applyVolumeDelta(grid, advectedVolume, config.minCellVolume());
    }

    private static void applyToWorld(ServerLevel level, FluidGrid grid, VolumetricFluidConfig.Values config, SimulatedFluid fluidType) {
        Set<Long> controlledNow = new HashSet<>();
        boolean replacingVanillaEngine = switch (fluidType) {
            case WATER -> config.replaceVanillaWaterEngine();
            case LAVA -> config.replaceVanillaLavaEngine();
        };
        double placeThreshold = replacingVanillaEngine ? config.minCellVolume() : config.placeThreshold();

        for (Map.Entry<Long, FluidCell> entry : grid.cells.entrySet()) {
            long packedPos = entry.getKey();
            FluidCell cell = entry.getValue();
            BlockPos pos = BlockPos.of(packedPos);

            if (!level.isLoaded(pos)) {
                continue;
            }

            if (cell.volume >= placeThreshold) {
                BlockState state = level.getBlockState(pos);
                BlockState simulatedState = fluidStateForVolume(fluidType, cell.volume);
                if (state.isAir() || state.is(fluidType.block())) {
                    if (!state.equals(simulatedState)) {
                        level.setBlockAndUpdate(pos, simulatedState);
                    }
                    controlledNow.add(packedPos);
                }
            }
        }

        for (Long packedPos : new ArrayList<>(grid.controlledBlocks)) {
            FluidCell cell = grid.cells.get(packedPos);
            double volume = cell == null ? 0.0D : cell.volume;
            if (volume > config.removeThreshold()) {
                continue;
            }
            BlockPos pos = BlockPos.of(packedPos);
            if (!shouldRemoveControlledFluidBlock(level, pos, fluidType, replacingVanillaEngine)) {
                continue;
            }
            level.setBlockAndUpdate(pos, Blocks.AIR.defaultBlockState());
        }


        grid.controlledBlocks.clear();
        grid.controlledBlocks.addAll(controlledNow);
    }


    private static boolean shouldRemoveControlledFluidBlock(ServerLevel level,
                                                            BlockPos pos,
                                                            SimulatedFluid fluidType,
                                                            boolean replacingVanillaEngine) {
        if (!level.isLoaded(pos)) {
            return false;
        }
        BlockState state = level.getBlockState(pos);
        if (!state.is(fluidType.block())) {
            return false;
        }
        if (!replacingVanillaEngine) {
            return true;
        }
        // In replacement mode, keep source blocks so oceans/lakes remain stable while allowing low-volume
        // flowing water to dissipate naturally.
        return !state.getFluidState().isSource();
    }

    private static BlockState fluidStateForVolume(SimulatedFluid fluidType, double volume) {
        BlockState base = fluidType.block().defaultBlockState();
        IntegerProperty flowingProperty = BlockStateProperties.LEVEL_FLOWING;
        if (!base.hasProperty(flowingProperty)) {
            return base;
        }
        // Minecraft liquid levels: 0 = full/source, 1..7 = descending heights.
        int level = Math.max(0, Math.min(7, 7 - (int) Math.round(Math.max(0.0D, Math.min(1.0D, volume)) * 7.0D)));
        return base.setValue(flowingProperty, level);
    }

    private static boolean isNearAnyPlayer(ServerLevel level, BlockPos pos, int radiusSq) {
        for (ServerPlayer player : level.players()) {
            if (player.blockPosition().distSqr(pos) <= radiusSq) {
                return true;
            }
        }
        return false;
    }

    private static boolean isFluidPassable(ServerLevel level, BlockPos pos, SimulatedFluid fluidType) {
        if (!level.isLoaded(pos)) {
            return false;
        }
        BlockState state = level.getBlockState(pos);
        return state.isAir() || state.is(fluidType.block()) || state.canBeReplaced();
    }

    private static void accumulate(Map<Long, Double> delta, long packedPos, double value) {
        delta.merge(packedPos, value, Double::sum);
    }

    private static void applyVolumeDelta(FluidGrid grid, Map<Long, Double> delta, double minCellVolume) {
        for (Map.Entry<Long, Double> update : delta.entrySet()) {
            FluidCell cell = grid.cells.computeIfAbsent(update.getKey(), ignored -> new FluidCell());
            cell.volume = Math.max(0.0D, Math.min(1.0D, cell.volume + update.getValue()));
            if (cell.volume <= minCellVolume) {
                grid.cells.remove(update.getKey());
            }
        }
    }

    private static void applyVelocityDamping(FluidGrid grid, double damping) {
        for (FluidCell cell : grid.cells.values()) {
            cell.vx *= damping;
            cell.vy *= damping;
            cell.vz *= damping;
        }
    }

    private static void applyViscosityDiffusion(FluidGrid grid, VolumetricFluidConfig.Values config) {
        if (config.viscosity() <= 0.0D || grid.cells.isEmpty()) {
            return;
        }
        Map<Long, double[]> smoothed = new HashMap<>();
        for (Map.Entry<Long, FluidCell> entry : grid.cells.entrySet()) {
            BlockPos pos = BlockPos.of(entry.getKey());
            FluidCell cell = entry.getValue();
            double avgVx = cell.vx;
            double avgVy = cell.vy;
            double avgVz = cell.vz;
            int samples = 1;
            for (Direction direction : Direction.values()) {
                FluidCell neighbor = grid.cells.get(pos.relative(direction).asLong());
                if (neighbor == null) {
                    continue;
                }
                avgVx += neighbor.vx;
                avgVy += neighbor.vy;
                avgVz += neighbor.vz;
                samples++;
            }
            double blend = Math.min(1.0D, config.viscosity());
            double targetVx = avgVx / samples;
            double targetVy = avgVy / samples;
            double targetVz = avgVz / samples;
            smoothed.put(entry.getKey(), new double[]{
                    lerp(cell.vx, targetVx, blend),
                    lerp(cell.vy, targetVy, blend),
                    lerp(cell.vz, targetVz, blend)
            });
        }
        for (Map.Entry<Long, double[]> entry : smoothed.entrySet()) {
            FluidCell cell = grid.cells.get(entry.getKey());
            if (cell == null) {
                continue;
            }
            double[] velocity = entry.getValue();
            cell.vx = velocity[0];
            cell.vy = velocity[1];
            cell.vz = velocity[2];
        }
    }

    private static void applyVorticityConfinement(FluidGrid grid, VolumetricFluidConfig.Values config) {
        if (config.vorticity() <= 0.0D || grid.cells.isEmpty()) {
            return;
        }
        double strength = config.vorticity() * 0.10D;
        for (Map.Entry<Long, FluidCell> entry : grid.cells.entrySet()) {
            BlockPos pos = BlockPos.of(entry.getKey());
            FluidCell center = entry.getValue();
            FluidCell east = grid.cells.get(pos.east().asLong());
            FluidCell west = grid.cells.get(pos.west().asLong());
            FluidCell north = grid.cells.get(pos.north().asLong());
            FluidCell south = grid.cells.get(pos.south().asLong());

            double dVzdx = ((east == null ? center.vz : east.vz) - (west == null ? center.vz : west.vz)) * 0.5D;
            double dVxdz = ((south == null ? center.vx : south.vx) - (north == null ? center.vx : north.vx)) * 0.5D;
            double curlY = dVzdx - dVxdz;

            center.vx += -curlY * strength;
            center.vz += curlY * strength;
        }
    }

    private static void applyTurbulence(ServerLevel level, FluidGrid grid, VolumetricFluidConfig.Values config) {
        if (config.turbulence() <= 0.0D || grid.cells.isEmpty()) {
            return;
        }
        double amplitude = config.turbulence() * 0.05D;
        for (Map.Entry<Long, FluidCell> entry : grid.cells.entrySet()) {
            BlockPos pos = BlockPos.of(entry.getKey());
            FluidCell cell = entry.getValue();
            double flowSpeed = Math.sqrt(cell.vx * cell.vx + cell.vz * cell.vz);
            if (flowSpeed < 0.01D) {
                continue;
            }
            double shorelineFactor = computeShorelineFactor(level, pos);
            double noise = pseudoNoise(pos.getX(), pos.getY(), pos.getZ(), level.getGameTime());
            double injection = amplitude * (0.5D + shorelineFactor) * noise;
            cell.vx += injection;
            cell.vz -= injection * 0.7D;
        }
    }

    private static void pruneCells(FluidGrid grid, double minCellVolume) {
        grid.cells.entrySet().removeIf(entry -> entry.getValue().volume <= minCellVolume);
    }

    private static double directionalMomentum(FluidCell cell, Direction direction) {
        return switch (direction) {
            case EAST -> cell.vx;
            case WEST -> -cell.vx;
            case SOUTH -> cell.vz;
            case NORTH -> -cell.vz;
            default -> 0.0D;
        };
    }

    private static double lerp(double from, double to, double alpha) {
        return from + (to - from) * alpha;
    }

    private static double pseudoNoise(int x, int y, int z, long t) {
        long seed = x * 73428767L ^ y * 912931L ^ z * 438289L ^ t * 28711L;
        seed ^= (seed << 13);
        seed ^= (seed >>> 7);
        seed ^= (seed << 17);
        return ((seed & 1023L) / 511.5D) - 1.0D;
    }

    private static VolumetricFluidConfig.Values loadConfigWithFallback() {
        try {
            return VolumetricFluidConfig.values();
        } catch (IllegalStateException e) {
            ModConstants.LOGGER.warn("Volumetric fluid config accessed before load; using defaults. ({})", e.getMessage());
            return VolumetricFluidConfig.defaultValues();
        }
    }

    private static FluidGrid getGrid(ServerLevel level, SimulatedFluid fluidType) {
        Map<SimulatedFluid, FluidGrid> byFluid = GRIDS.computeIfAbsent(level.dimension(), ignored -> new ConcurrentHashMap<>());
        return byFluid.computeIfAbsent(fluidType, ignored -> new FluidGrid());
    }

    private static FluidGrid getGridOrNull(ServerLevel level, SimulatedFluid fluidType) {
        Map<SimulatedFluid, FluidGrid> byFluid = GRIDS.get(level.dimension());
        if (byFluid == null) {
            return null;
        }
        return byFluid.get(fluidType);
    }

    private static final class FluidGrid {
        private final Map<Long, FluidCell> cells = new HashMap<>();
        private final Set<Long> controlledBlocks = new HashSet<>();
    }

    private static final class FluidCell {
        private double volume;
        private double vx;
        private double vy;
        private double vz;
    }

    public record SimulationSnapshot(int activeCells, int controlledBlocks, double totalVolume, double averageSpeed) {
    }

    public record SurfaceSample(int blockX,
                                int blockZ,
                                double surfaceY,
                                double volume,
                                double shorelineFactor,
                                double tideOffset,
                                double moonPhaseFactor,
                                SimulatedFluid fluidType) {
    }

    private static double computeShorelineFactor(ServerLevel level, BlockPos pos) {
        double biomeBoost = level.getBiome(pos).is(BiomeTags.IS_BEACH) ? 0.6D : 0.0D;
        int sandHits = 0;
        int edgeHits = 0;
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        for (Direction direction : LATERALS) {
            cursor.setWithOffset(pos, direction);
            if (!level.isLoaded(cursor)) {
                continue;
            }
            BlockState state = level.getBlockState(cursor);
            if (state.is(Blocks.SAND) || state.is(Blocks.RED_SAND) || state.is(Blocks.SANDSTONE)) {
                sandHits++;
            }
            if (!state.isAir() && !state.liquid()) {
                edgeHits++;
            }
        }
        double sandFactor = sandHits / 4.0D;
        double edgeFactor = edgeHits / 4.0D;
        return Math.max(0.0D, Math.min(1.0D, biomeBoost + sandFactor * 0.6D + edgeFactor * 0.25D));
    }

    public enum SimulatedFluid {
        WATER(Fluids.WATER, Blocks.WATER),
        LAVA(Fluids.LAVA, Blocks.LAVA);

        private final Fluid fluid;
        private final net.minecraft.world.level.block.Block block;

        SimulatedFluid(Fluid fluid, net.minecraft.world.level.block.Block block) {
            this.fluid = fluid;
            this.block = block;
        }

        public Fluid fluid() {
            return fluid;
        }

        public net.minecraft.world.level.block.Block block() {
            return block;
        }
    }
}
