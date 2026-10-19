package com.thunder.wildernessodysseyapi.watersystem.water.worldgen.river;

import com.thunder.wildernessodysseyapi.core.ModAttachments;
import com.thunder.wildernessodysseyapi.watersystem.water.worldgen.GenerationWaterStateMapper;
import com.thunder.wildernessodysseyapi.watersystem.water.worldgen.river.bank.RiverBankProfiles;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ProtoChunk;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.material.FlowingFluid;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Predicate;

/**
 * Applies only the requesting chunk's certified slice. Prediction and raster work
 * run outside the short mutation lock; no chunk request, future or background
 * task is ever awaited here. The fixed lock prevents a neighboring slice's
 * partially stored curtain from being mistaken for changed natural terrain.
 */
public final class RiverConnectedGeneration {
    private static final Object MUTATIONS = new Object();
    private static final int[][] DIRECTIONS = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
    private RiverConnectedGeneration() { }

    /** The provider may return only already-admitted CARVERS dependencies; null is a veto. */
    public static boolean placeSlice(long seed, ChunkAccess chunk, RiverChannelShape.Shape shape,
                                     RiverGenerationSettings settings,
                                     BiFunction<Integer, Integer, ChunkAccess> admitted, Predicate<BlockPos> canWrite) {
        return placeSlice(seed, chunk, shape, settings, admitted, canWrite, ignored -> { });
    }

    /** Reports at most one bounded admission failure; no per-block logging occurs. */
    public static boolean placeSlice(long seed, ChunkAccess chunk, RiverChannelShape.Shape shape,
                                     RiverGenerationSettings settings,
                                     BiFunction<Integer, Integer, ChunkAccess> admitted, Predicate<BlockPos> canWrite,
                                     Consumer<Rejection> rejected) {
        synchronized (MUTATIONS) {
            if (!settings.enabled() || !(chunk instanceof ProtoChunk) || chunk.isUpgrading()
                    || chunk.getPersistedStatus().isOrAfter(ChunkStatus.FEATURES) || protectedChunk(chunk))
                return reject(rejected, Reason.CHUNK, chunk.getPos().getWorldPosition());
            var capture = chunk.getExistingData(ModAttachments.RIVER_GENERATION).orElse(null);
            if (capture == null || !capture.hasTerrain(settings.fingerprint()))
                return reject(rejected, Reason.CAPTURE, chunk.getPos().getWorldPosition());
            List<RiverChannelColumn> columns = new ArrayList<>();
            int minimumX = chunk.getPos().getMinBlockX();
            int minimumZ = chunk.getPos().getMinBlockZ();
            int writes = 0;
            long basin = 0;
            for (var column : shape.columns()) {
                int x = column.x() - minimumX;
                int z = column.z() - minimumZ;
                if (x < 0 || x >= 16 || z < 0 || z >= 16) continue;
                int drop = column.topY() - column.waterY();
                if (drop > shape.maximumDrop() || drop > 1 && !shape.waterfalls())
                    return reject(rejected, Reason.DROP, new BlockPos(column.x(), column.waterY(), column.z()));
                if (capture.water(x, z)) {
                    // Existing receivers keep their exact native states/inventory.
                    if (capture.height(x, z) != column.waterY() || !sourceWater(chunk,
                            new BlockPos(column.x(), column.waterY(), column.z())))
                        return reject(rejected, Reason.RECEIVER, new BlockPos(column.x(), column.waterY(), column.z()));
                    continue;
                }
                int ground = capture.height(x, z);
                if (ground - column.floorY() > settings.maximumCut())
                    return reject(rejected, Reason.CUT, new BlockPos(column.x(), column.floorY(), column.z()));
                if (column.waterY() > ground || column.floorY() - 1 > ground)
                    return reject(rejected, Reason.SUSPENDED, new BlockPos(column.x(), column.waterY(), column.z()));
                if (!certifyColumn(chunk, column, ground, canWrite, rejected)) return false;
                for (int[] direction : DIRECTIONS) {
                    int nx = column.x() + direction[0];
                    int nz = column.z() + direction[1];
                    ChunkAccess neighbor = admitted.apply(nx >> 4, nz >> 4);
                    if (neighbor == null) return reject(rejected, Reason.DEPENDENCY, new BlockPos(nx, column.waterY(), nz));
                    var next = shape.at(nx, nz);
                    var evidence = neighbor.getExistingData(ModAttachments.RIVER_GENERATION).orElse(null);
                    boolean pending = neighbor instanceof ProtoChunk && !neighbor.isUpgrading()
                            && !neighbor.getPersistedStatus().isOrAfter(ChunkStatus.FEATURES)
                            && evidence != null && evidence.hasTerrain(settings.fingerprint());
                    if (next != null) {
                        int neighborDrop = next.topY() - next.waterY();
                        if (neighborDrop > shape.maximumDrop() || neighborDrop > 1 && !shape.waterfalls())
                            return reject(rejected, Reason.DROP, new BlockPos(nx, next.waterY(), nz));
                        // Completed neighbors normally arrive wrapped in an
                        // ImposterProtoChunk. Class identity cannot certify new
                        // terrain; status and the matching capture are required.
                        if (!pending && !placedReach(neighbor, next)
                                && !sourceWater(neighbor, new BlockPos(nx, next.waterY(), nz)))
                            return reject(rejected, Reason.OLD_NEIGHBOR, new BlockPos(nx, column.waterY(), nz),
                                    "expected=" + next + ", actual=" + (evidence == null ? null : evidence.reachAt(nx & 15, nz & 15))
                                            + ", status=" + neighbor.getPersistedStatus());
                        if (pending && protectedChunk(neighbor))
                            return reject(rejected, Reason.STRUCTURE, new BlockPos(nx, column.waterY(), nz));
                        if (pending && evidence.water(nx & 15, nz & 15)) {
                            if (evidence.height(nx & 15, nz & 15) != next.waterY()
                                    || !sourceWater(neighbor, new BlockPos(nx, next.waterY(), nz)))
                                return reject(rejected, Reason.RECEIVER, new BlockPos(nx, next.waterY(), nz));
                        } else if (pending && (evidence.height(nx & 15, nz & 15) - next.floorY() > settings.maximumCut()
                                || next.waterY() > evidence.height(nx & 15, nz & 15)
                                || !evidence.naturalAt(nx & 15, next.floorY(), nz & 15)
                                || !evidence.naturalAt(nx & 15, next.floorY() - 1, nz & 15)))
                            return reject(rejected, Reason.NEIGHBOR_SUPPORT, new BlockPos(nx, next.floorY(), nz));
                        if (pending && !evidence.water(nx & 15, nz & 15)
                                && !certifyColumn(neighbor, next, evidence.height(nx & 15, nz & 15), canWrite, rejected))
                            return false;
                    }
                    for (int y = column.floorY() + 1; y <= column.waterY(); y++) {
                        BlockPos wall = new BlockPos(nx, y, nz);
                        if (next != null && y > next.floorY() && y <= next.topY()) {
                            var state = neighbor.getBlockState(wall);
                            boolean plannedWater = GenerationWaterStateMapper.isWildernessWater(state)
                                    && (state.getFluidState().isSource()
                                    || y > next.waterY() && state.getFluidState().getValue(FlowingFluid.FALLING));
                            // The future curtain may legitimately occupy air
                            // above a certified natural column. Native receivers
                            // are preserved and cannot promise that future write.
                            boolean futureCurtain = pending && !evidence.water(nx & 15, nz & 15)
                                    && y > next.waterY() && y > evidence.height(nx & 15, nz & 15) && clearable(state);
                            if (!RiverTerrainCapture.natural(state) && !plannedWater && !sourceWater(neighbor, wall) && !futureCurtain)
                                return reject(rejected, Reason.NEIGHBOR_COLUMN, wall, "state=" + state + ", expected=" + next);
                            if (!sourceWater(neighbor, new BlockPos(nx, next.waterY(), nz))
                                    && !RiverTerrainCapture.natural(neighbor.getBlockState(new BlockPos(nx, next.floorY() - 1, nz))))
                                return reject(rejected, Reason.NEIGHBOR_SUPPORT, wall);
                        } else if (!RiverTerrainCapture.natural(neighbor.getBlockState(wall))
                                && !sourceWater(neighbor, wall)) return reject(rejected, Reason.BANK, wall);
                    }
                }
                writes += Math.max(ground, column.topY()) - column.floorY() + 2;
                if (writes > settings.maximumWrites())
                    return reject(rejected, Reason.WRITE_BUDGET, new BlockPos(column.x(), column.waterY(), column.z()));
                columns.add(new RiverChannelColumn(x, z, column.waterY(), column.floorY(), column.topY(),
                        column.directionX(), column.directionZ(), column.speed(), column.kind()));
                if (basin == 0) basin = column.basinId();
            }
            // Whole-slice admission precedes the first physical or attachment write.
            for (var column : columns) {
                var position = new BlockPos.MutableBlockPos(minimumX + column.x(), column.floorY(), minimumZ + column.z());
                RiverNetworkFeature.store(chunk, position, settings.naturalBanks()
                        ? RiverBankProfiles.bed(chunk, position, column.kind()) : chunk.getBlockState(position), column);
                int top = Math.max(capture.height(column.x(), column.z()), column.topY());
                for (int y = column.floorY() + 1; y <= top; y++) {
                    position.setY(y);
                    var state = y <= column.topY() ? GenerationWaterStateMapper.map(Blocks.WATER.defaultBlockState())
                            : Blocks.AIR.defaultBlockState();
                    if (y > column.waterY() && y <= column.topY() && state.getFluidState().getType() instanceof FlowingFluid fluid)
                        state = fluid.getFlowing(8, true).createLegacyBlock();
                    RiverNetworkFeature.store(chunk, position, state, column);
                }
                position.setY(top + 1);
                if (!chunk.getBlockState(position).isAir())
                    RiverNetworkFeature.store(chunk, position, Blocks.AIR.defaultBlockState(), column);
            }
            capture.complete(basin, 0, 0, columns);
            chunk.setUnsaved(true);
            return !columns.isEmpty();
        }
    }

    public enum Reason { CHUNK, CAPTURE, RECEIVER, CUT, SUSPENDED, COLUMN, DEPENDENCY, OLD_NEIGHBOR,
        STRUCTURE, NEIGHBOR_COLUMN, NEIGHBOR_SUPPORT, BANK, WRITE_BUDGET, WRITE_AREA, CLEARANCE, DROP }
    public record Rejection(Reason reason, BlockPos position, String detail) {
        public Rejection(Reason reason, BlockPos position) { this(reason, position, ""); }
    }

    private static boolean reject(Consumer<Rejection> rejected, Reason reason, BlockPos position) {
        return reject(rejected, reason, position, "");
    }

    private static boolean reject(Consumer<Rejection> rejected, Reason reason, BlockPos position, String detail) {
        rejected.accept(new Rejection(reason, position.immutable(), detail.length() > 512 ? detail.substring(0, 512) : detail));
        return false;
    }

    private static boolean certifyColumn(ChunkAccess chunk, RiverChannelShape.Column column, int ground,
                                          Predicate<BlockPos> canWrite, Consumer<Rejection> rejected) {
        for (int y = column.floorY() - 1; y <= Math.max(ground, column.topY()) + 1; y++) {
            var position = new BlockPos(column.x(), y, column.z());
            if (y < chunk.getMinBuildHeight() || y >= chunk.getMaxBuildHeight() || !canWrite.test(position))
                return reject(rejected, Reason.WRITE_AREA, position);
            var state = chunk.getBlockState(position);
            if (y <= ground && !RiverTerrainCapture.natural(state)) return reject(rejected, Reason.COLUMN, position);
            if (y > ground && !clearable(state))
                return reject(rejected, Reason.CLEARANCE, position);
        }
        return true;
    }

    private static boolean clearable(net.minecraft.world.level.block.state.BlockState state) {
        return state.isAir() || state.canBeReplaced() && state.getFluidState().isEmpty() && !state.hasBlockEntity();
    }

    private static boolean sourceWater(ChunkAccess chunk, BlockPos position) {
        var state = chunk.getBlockState(position);
        return (state.is(Blocks.WATER) || GenerationWaterStateMapper.isWildernessWater(state)) && state.getFluidState().isSource();
    }

    private static boolean placedReach(ChunkAccess chunk, RiverChannelShape.Column expected) {
        var metadata = chunk.getExistingData(ModAttachments.RIVER_GENERATION).orElse(null);
        var reach = metadata == null ? null : metadata.reachAt(expected.x() & 15, expected.z() & 15);
        if (reach == null || reach.waterY() != expected.waterY() || reach.floorY() != expected.floorY()
                || reach.topY() != expected.topY() || reach.kind() != expected.kind()) return false;
        for (int y = reach.floorY() + 1; y <= reach.topY(); y++) {
            var state = chunk.getBlockState(new BlockPos(expected.x(), y, expected.z()));
            if (!GenerationWaterStateMapper.isWildernessWater(state)) return false;
            var fluid = state.getFluidState();
            if (y <= reach.waterY() ? !fluid.isSource() : !fluid.getValue(FlowingFluid.FALLING)) return false;
        }
        return RiverTerrainCapture.natural(chunk.getBlockState(new BlockPos(expected.x(), reach.floorY(), expected.z())))
                && RiverTerrainCapture.natural(chunk.getBlockState(new BlockPos(expected.x(), reach.floorY() - 1, expected.z())));
    }

    private static boolean protectedChunk(ChunkAccess chunk) {
        return chunk.isUpgrading() || chunk.getAllStarts().values().stream().anyMatch(start -> start.isValid())
                || chunk.getAllReferences().values().stream().anyMatch(references -> !references.isEmpty());
    }
}
