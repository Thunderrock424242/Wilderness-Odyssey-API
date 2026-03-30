package com.thunder.wildernessodysseyapi.watersystem.volumetric;

import com.thunder.wildernessodysseyapi.core.ModConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
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
    private static final Map<ResourceKey<Level>, FluidGrid> GRIDS = new ConcurrentHashMap<>();
    private static VolumetricFluidConfig.Values cachedConfig = VolumetricFluidConfig.defaultValues();

    private VolumetricFluidManager() {
    }

    public static void reloadConfig() {
        cachedConfig = loadConfigWithFallback();
    }

    public static boolean shouldReplaceVanillaWaterEngine() {
        return cachedConfig.enabled() && cachedConfig.replaceVanillaWaterEngine();
    }

    public static void ingestVanillaWaterTick(ServerLevel level, BlockPos pos, double normalizedVolume) {
        if (!cachedConfig.enabled()) {
            return;
        }
        FluidGrid grid = GRIDS.computeIfAbsent(level.dimension(), ignored -> new FluidGrid());
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
            FluidGrid grid = GRIDS.computeIfAbsent(level.dimension(), ignored -> new FluidGrid());
            ingestWaterNearPlayers(level, grid, cachedConfig);
            stepSimulation(level, grid, cachedConfig);
            applyToWorld(level, grid, cachedConfig);
        }
    }

    public static SimulationSnapshot getSnapshot(ServerLevel level) {
        FluidGrid grid = GRIDS.computeIfAbsent(level.dimension(), ignored -> new FluidGrid());
        double totalVolume = 0.0D;
        double totalSpeed = 0.0D;
        for (FluidCell cell : grid.cells.values()) {
            totalVolume += cell.volume;
            totalSpeed += Math.sqrt(cell.vx * cell.vx + cell.vy * cell.vy + cell.vz * cell.vz);
        }
        double avgSpeed = grid.cells.isEmpty() ? 0.0D : totalSpeed / grid.cells.size();
        return new SimulationSnapshot(grid.cells.size(), grid.controlledBlocks.size(), totalVolume, avgSpeed);
    }

    public static void clear(ServerLevel level) {
        FluidGrid grid = GRIDS.get(level.dimension());
        if (grid == null) {
            return;
        }
        for (Long packedPos : new ArrayList<>(grid.controlledBlocks)) {
            BlockPos pos = BlockPos.of(packedPos);
            if (level.getBlockState(pos).is(Blocks.WATER)) {
                level.setBlockAndUpdate(pos, Blocks.AIR.defaultBlockState());
            }
        }
        grid.cells.clear();
        grid.controlledBlocks.clear();
    }

    public static int seedFromExistingWater(ServerLevel level, BlockPos center, int radius) {
        FluidGrid grid = GRIDS.computeIfAbsent(level.dimension(), ignored -> new FluidGrid());
        int injected = 0;
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        int minY = Math.max(level.getMinBuildHeight(), center.getY() - radius);
        int maxY = Math.min(level.getMaxBuildHeight() - 1, center.getY() + radius);

        for (int x = center.getX() - radius; x <= center.getX() + radius; x++) {
            for (int y = minY; y <= maxY; y++) {
                for (int z = center.getZ() - radius; z <= center.getZ() + radius; z++) {
                    cursor.set(x, y, z);
                    if (!level.isLoaded(cursor) || level.getFluidState(cursor).getType() != Fluids.WATER) {
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

    private static void ingestWaterNearPlayers(ServerLevel level, FluidGrid grid, VolumetricFluidConfig.Values config) {
        int radius = config.playerSampleRadius();
        int step = Math.max(1, config.playerSampleStep());
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();

        for (ServerPlayer player : level.players()) {
            BlockPos origin = player.blockPosition();
            int minY = Math.max(level.getMinBuildHeight(), origin.getY() - radius);
            int maxY = Math.min(level.getMaxBuildHeight() - 1, origin.getY() + radius);
            for (int x = origin.getX() - radius; x <= origin.getX() + radius; x += step) {
                for (int y = minY; y <= maxY; y += step) {
                    for (int z = origin.getZ() - radius; z <= origin.getZ() + radius; z += step) {
                        cursor.set(x, y, z);
                        if (!level.isLoaded(cursor) || level.getFluidState(cursor).getType() != Fluids.WATER) {
                            continue;
                        }
                        FluidCell cell = grid.cells.computeIfAbsent(cursor.asLong(), ignored -> new FluidCell());
                        cell.volume = Math.max(cell.volume, Math.max(0.125D, level.getFluidState(cursor).getAmount() / 8.0D));
                    }
                }
            }
        }
    }

    private static void stepSimulation(ServerLevel level, FluidGrid grid, VolumetricFluidConfig.Values config) {
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
                if (below.getY() >= level.getMinBuildHeight() && isFluidPassable(level, below)) {
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
                    if (!isFluidPassable(level, sidePos)) {
                        continue;
                    }

                    FluidCell neighbor = grid.cells.get(sidePos.asLong());
                    double neighborVolume = neighbor == null ? 0.0D : neighbor.volume;
                    double gradient = (cell.volume - neighborVolume);
                    if (gradient <= config.minCellVolume()) {
                        continue;
                    }

                    double flow = Math.min(remaining,
                            Math.min(config.lateralTransfer(), gradient * config.pressureStrength()));
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

        advectByVelocity(level, grid, config);
        applyVelocityDamping(grid, config.inertiaDamping());
        pruneCells(grid, config.minCellVolume());
    }

    private static void advectByVelocity(ServerLevel level, FluidGrid grid, VolumetricFluidConfig.Values config) {
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
            if (!isFluidPassable(level, target)) {
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

    private static void applyToWorld(ServerLevel level, FluidGrid grid, VolumetricFluidConfig.Values config) {
        Set<Long> controlledNow = new HashSet<>();

        for (Map.Entry<Long, FluidCell> entry : grid.cells.entrySet()) {
            long packedPos = entry.getKey();
            FluidCell cell = entry.getValue();
            BlockPos pos = BlockPos.of(packedPos);

            if (!level.isLoaded(pos)) {
                continue;
            }

            if (cell.volume >= config.placeThreshold()) {
                BlockState state = level.getBlockState(pos);
                boolean wasControlled = grid.controlledBlocks.contains(packedPos);
                if (state.isAir()) {
                    level.setBlockAndUpdate(pos, Blocks.WATER.defaultBlockState());
                    controlledNow.add(packedPos);
                } else if (wasControlled && state.is(Blocks.WATER)) {
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
            if (level.isLoaded(pos) && level.getBlockState(pos).is(Blocks.WATER)) {
                level.setBlockAndUpdate(pos, Blocks.AIR.defaultBlockState());
            }
        }

        grid.controlledBlocks.clear();
        grid.controlledBlocks.addAll(controlledNow);
    }

    private static boolean isNearAnyPlayer(ServerLevel level, BlockPos pos, int radiusSq) {
        for (ServerPlayer player : level.players()) {
            if (player.blockPosition().distSqr(pos) <= radiusSq) {
                return true;
            }
        }
        return false;
    }

    private static boolean isFluidPassable(ServerLevel level, BlockPos pos) {
        if (!level.isLoaded(pos)) {
            return false;
        }
        BlockState state = level.getBlockState(pos);
        return state.isAir() || state.is(Blocks.WATER) || state.canBeReplaced();
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

    private static void pruneCells(FluidGrid grid, double minCellVolume) {
        grid.cells.entrySet().removeIf(entry -> entry.getValue().volume <= minCellVolume);
    }

    private static VolumetricFluidConfig.Values loadConfigWithFallback() {
        try {
            return VolumetricFluidConfig.values();
        } catch (IllegalStateException e) {
            ModConstants.LOGGER.warn("Volumetric fluid config accessed before load; using defaults. ({})", e.getMessage());
            return VolumetricFluidConfig.defaultValues();
        }
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
}
