package com.thunder.wildernessodysseyapi.weather.client.surface;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.thunder.wildernessodysseyapi.rendering.compat.ShaderPackCompatibility;
import com.thunder.wildernessodysseyapi.weather.api.SurfaceWeatherState;
import com.thunder.wildernessodysseyapi.weather.api.WeatherSample;
import com.thunder.wildernessodysseyapi.weather.client.ClientWeatherCoordinator;
import com.thunder.wildernessodysseyapi.weather.config.WeatherRenderingConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.levelgen.Heightmap;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.Comparator;
import java.util.function.IntBinaryOperator;

/**
 * Draws bounded wetness, puddle and cosmetic snow contours from synchronized surface memory.
 *
 * <p>Continuous world-space noise is triangulated across block boundaries, so
 * neighboring samples join into irregular shapes. Puddles require a perfectly
 * flat, loaded, sky-visible solid surface; wetness tolerates a one-block slope.</p>
 */
public final class WeatherSurfaceRenderer {

    private static final long WET_SALT = 0x9E3779B97F4A7C15L;
    private static final long PUDDLE_SALT = 0xC2B2AE3D27D4EB4FL;
    private static final long SNOW_SALT = 0xD6E8FEB86659FD93L;
    private static final List<SurfaceTriangle> TRIANGLES = new ArrayList<>();
    private static final SurfaceHeightCache HEIGHTS = new SurfaceHeightCache();
    private static final SurfaceCandidateScan CANDIDATES = new SurfaceCandidateScan();
    private static final Map<Long, List<SurfaceTriangle>> PATCHES = new HashMap<>();

    private static ClientLevel cachedLevel;
    private static int cachedX = Integer.MIN_VALUE;
    private static int cachedZ = Integer.MIN_VALUE;
    private static long cachedTick = Long.MIN_VALUE;
    private static Diagnostics diagnostics = Diagnostics.INACTIVE;

    private WeatherSurfaceRenderer() {
    }

    /** Renders after translucent blocks so water and wet ground blend consistently. */
    public static void render(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        ClientLevel level = minecraft.level;
        WeatherRenderingConfig.Settings settings = WeatherRenderingConfig.settings();
        if (level == null || !settings.surfaceOverlays()
                || ShaderPackCompatibility.isExternalShaderPackActive()
                || !ClientWeatherCoordinator.controls(level)) {
            clear();
            return;
        }
        var camera = event.getCamera().getPosition();
        refresh(level, (int) Math.floor(camera.x), (int) Math.floor(camera.z), settings);
        if (TRIANGLES.isEmpty()) {
            return;
        }

        var buffers = minecraft.renderBuffers().bufferSource();
        var renderType = WeatherSurfaceRenderTypes.wetSurface();
        VertexConsumer vertices = buffers.getBuffer(renderType);
        PoseStack poses = event.getPoseStack();
        poses.pushPose();
        poses.translate(-camera.x, -camera.y, -camera.z);
        var matrix = poses.last().pose();
        for (SurfaceTriangle triangle : TRIANGLES) {
            boolean snow = triangle.kind == SurfaceKind.SNOW;
            boolean puddle = triangle.kind == SurfaceKind.PUDDLE;
            int red = snow ? 229 : puddle ? 58 : 24;
            int green = snow ? 239 : puddle ? 78 : 34;
            int blue = snow ? 245 : puddle ? 96 : 39;
            int alpha = (int) (255.0F * (snow ? 0.20F + triangle.strength * 0.65F
                    : puddle ? 0.07F + triangle.strength * 0.16F
                    : 0.035F + triangle.strength * 0.085F));
            float y = triangle.y + (snow ? 0.004F : puddle ? 0.0025F : 0.0012F);
            vertices.addVertex(matrix, triangle.x0, y, triangle.z0).setColor(red, green, blue, alpha);
            vertices.addVertex(matrix, triangle.x1, y, triangle.z1).setColor(red, green, blue, alpha);
            vertices.addVertex(matrix, triangle.x2, y, triangle.z2).setColor(red, green, blue, alpha);
        }
        poses.popPose();
        buffers.endBatch(renderType);
    }

    /** Returns bounded mesh facts for the existing weather debug page. */
    public static Diagnostics diagnostics() {
        return diagnostics;
    }

    /** Clears cached terrain samples on disconnect and dimension changes. */
    public static void clear() {
        TRIANGLES.clear();
        HEIGHTS.clear();
        CANDIDATES.clear();
        PATCHES.clear();
        cachedLevel = null;
        cachedX = Integer.MIN_VALUE;
        cachedZ = Integer.MIN_VALUE;
        cachedTick = Long.MIN_VALUE;
        diagnostics = Diagnostics.INACTIVE;
    }

    private static void refresh(
            ClientLevel level,
            int centerX,
            int centerZ,
            WeatherRenderingConfig.Settings settings
    ) {
        long tick = level.getGameTime();
        if (cachedLevel == level
                && Math.abs(centerX - cachedX) < 3
                && Math.abs(centerZ - cachedZ) < 3
                && tick - cachedTick < 10L) {
            return;
        }
        if (cachedLevel != level) {
            HEIGHTS.clear();
            CANDIDATES.clear();
            PATCHES.clear();
        }
        cachedLevel = level;
        cachedX = centerX;
        cachedZ = centerZ;
        cachedTick = tick;
        TRIANGLES.clear();

        int radius = settings.surfaceOverlayRadiusBlocks();
        int maximumCells = settings.maximumSurfacePatches();
        int terrainBudget = Math.min(4096, Math.max(256, maximumCells * 5));
        int candidateBudget = Math.min(4096, Math.max(256, maximumCells * 4));
        HEIGHTS.beginRefresh(centerX, centerZ, radius, tick, terrainBudget);
        CANDIDATES.configure(centerX, centerZ, radius);
        PATCHES.keySet().removeIf(key -> distanceSquared(key, centerX, centerZ) > (long) radius * radius);
        BlockPos.MutableBlockPos probe = new BlockPos.MutableBlockPos();
        IntBinaryOperator sampleHeight = (x, z) -> {
            probe.set(x, 64, z);
            return level.hasChunkAt(probe)
                    ? level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z)
                    : Integer.MIN_VALUE;
        };
        int terrainCandidates = 0;
        BlockPos.MutableBlockPos ground = new BlockPos.MutableBlockPos();
        BlockPos.MutableBlockPos sky = new BlockPos.MutableBlockPos();
        // Keep published patches between bounded scans. Resuming the next
        // candidate reaches outer wet terrain even when nearby columns are dry.
        for (int candidate = 0; candidate < Math.min(candidateBudget, CANDIDATES.size())
                && !HEIGHTS.exhausted(); candidate++) {
            var column = CANDIDATES.next();
            int x = column.x();
            int z = column.z();
            long key = ChunkPos.asLong(x, z);
            PATCHES.remove(key);
            TRIANGLES.clear();
            terrainCandidates++;
            int y = HEIGHTS.height(x, z, sampleHeight);
            if (y == Integer.MIN_VALUE) {
                continue;
            }
            ground.set(x, y - 1, z);
            sky.set(x, y, z);
            var blockState = level.getBlockState(ground);
            if (!level.getFluidState(ground).isEmpty()
                    || !blockState.isFaceSturdy(level, ground, Direction.UP)
                    || !level.canSeeSky(sky)) {
                continue;
            }

            WeatherSample sample = ClientWeatherCoordinator.sampleAt(level, sky);
            SurfaceWeatherState surface = sample.surface();
            if (surface.wetness() < 0.08D && surface.puddleCoverage() < 0.04D
                    && surface.snowpack() < 0.025D) continue;
            int north = HEIGHTS.height(x, z - 1, sampleHeight);
            int east = HEIGHTS.height(x + 1, z, sampleHeight);
            int south = HEIGHTS.height(x, z + 1, sampleHeight);
            int west = HEIGHTS.height(x - 1, z, sampleHeight);
            boolean wetSuitable = surface.wetness() >= 0.08D
                    && SurfacePatchModel.flatEnough(y, north, east, south, west, 1);
            boolean puddleSuitable = surface.puddleCoverage() >= 0.04D
                    && SurfacePatchModel.flatEnough(y, north, east, south, west, 0);
            boolean snowSuitable = surface.snowpack() >= 0.025D
                    && SurfacePatchModel.flatEnough(y, north, east, south, west, 1);
            if (wetSuitable) {
                appendContour(x, y, z, surface.wetness(), SurfaceKind.WET, WET_SALT);
            }
            if (puddleSuitable) {
                appendContour(x, y, z, surface.puddleCoverage(), SurfaceKind.PUDDLE, PUDDLE_SALT);
            }
            if (snowSuitable) {
                appendContour(x, y, z, surface.snowpack(), SurfaceKind.SNOW, SNOW_SALT);
            }
            if (!TRIANGLES.isEmpty()) PATCHES.put(key, List.copyOf(TRIANGLES));
        }
        List<Long> published = PATCHES.keySet().stream()
                .sorted(Comparator.<Long>comparingLong(key -> distanceSquared(key, centerX, centerZ))
                        .thenComparingLong(Long::longValue)).limit(maximumCells).toList();
        PATCHES.keySet().retainAll(new java.util.HashSet<>(published));
        TRIANGLES.clear();
        int wetCells = 0;
        int puddleCells = 0;
        int snowCells = 0;
        for (long key : published) {
            List<SurfaceTriangle> triangles = PATCHES.get(key);
            if (triangles.stream().anyMatch(triangle -> triangle.kind == SurfaceKind.WET)) wetCells++;
            if (triangles.stream().anyMatch(triangle -> triangle.kind == SurfaceKind.PUDDLE)) puddleCells++;
            if (triangles.stream().anyMatch(triangle -> triangle.kind == SurfaceKind.SNOW)) snowCells++;
            TRIANGLES.addAll(triangles);
        }
        diagnostics = new Diagnostics(true, wetCells, puddleCells, TRIANGLES.size(), snowCells,
                terrainCandidates, HEIGHTS.probesThisRefresh(), HEIGHTS.size());
    }

    private static long distanceSquared(long key, int x, int z) {
        long dx = (long) ChunkPos.getX(key) - x;
        long dz = (long) ChunkPos.getZ(key) - z;
        return dx * dx + dz * dz;
    }

    private static void appendContour(
            int blockX,
            int y,
            int blockZ,
            double coverage,
            SurfaceKind kind,
            long salt
    ) {
        float northWest = SurfacePatchModel.field(blockX, blockZ, coverage, salt);
        float northEast = SurfacePatchModel.field(blockX + 1.0D, blockZ, coverage, salt);
        float southEast = SurfacePatchModel.field(blockX + 1.0D, blockZ + 1.0D, coverage, salt);
        float southWest = SurfacePatchModel.field(blockX, blockZ + 1.0D, coverage, salt);
        float strength = (float) Math.max(0.0D, Math.min(1.0D, coverage));
        for (SurfacePatchModel.Triangle triangle : SurfacePatchModel.triangulate(
                northWest,
                northEast,
                southEast,
                southWest
        )) {
            TRIANGLES.add(new SurfaceTriangle(
                    blockX + triangle.x0(), blockZ + triangle.z0(),
                    blockX + triangle.x1(), blockZ + triangle.z1(),
                    blockX + triangle.x2(), blockZ + triangle.z2(),
                    y,
                    strength,
                    kind
            ));
        }
    }

    private record SurfaceTriangle(
            float x0,
            float z0,
            float x1,
            float z1,
            float x2,
            float z2,
            float y,
            float strength,
            SurfaceKind kind
    ) {
    }

    /** Renderer facts kept separate from synchronized surface state. */
    public record Diagnostics(boolean active, int wetCells, int puddleCells, int triangles, int snowCells,
            int terrainCandidates, int terrainProbes, int cachedHeightColumns) {
        public static final Diagnostics INACTIVE = new Diagnostics(false, 0, 0, 0, 0);

        /** Preserves the previous diagnostics construction shape for integrations. */
        public Diagnostics(boolean active, int wetCells, int puddleCells, int triangles, int snowCells) {
            this(active, wetCells, puddleCells, triangles, snowCells, 0, 0, 0);
        }

        /** Preserves the previous diagnostics construction shape for integrations. */
        public Diagnostics(boolean active, int wetCells, int puddleCells, int triangles) {
            this(active, wetCells, puddleCells, triangles, 0);
        }
    }

    private enum SurfaceKind {
        WET,
        PUDDLE,
        SNOW
    }
}
