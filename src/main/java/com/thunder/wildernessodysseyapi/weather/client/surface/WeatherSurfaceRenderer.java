package com.thunder.wildernessodysseyapi.weather.client.surface;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.thunder.wildernessodysseyapi.weather.api.SurfaceWeatherState;
import com.thunder.wildernessodysseyapi.weather.api.WeatherSample;
import com.thunder.wildernessodysseyapi.weather.client.ClientWeatherCoordinator;
import com.thunder.wildernessodysseyapi.weather.config.WeatherRenderingConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.levelgen.Heightmap;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;

import java.util.ArrayList;
import java.util.List;

/**
 * Draws bounded connected wetness and puddle contours from synchronized surface memory.
 *
 * <p>Continuous world-space noise is triangulated across block boundaries, so
 * neighboring samples join into irregular shapes. Puddles require a perfectly
 * flat, loaded, sky-visible solid surface; wetness tolerates a one-block slope.</p>
 */
public final class WeatherSurfaceRenderer {

    private static final long WET_SALT = 0x9E3779B97F4A7C15L;
    private static final long PUDDLE_SALT = 0xC2B2AE3D27D4EB4FL;
    private static final List<SurfaceTriangle> TRIANGLES = new ArrayList<>();

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
        if (level == null || !settings.surfaceOverlays() || !ClientWeatherCoordinator.controls(level)) {
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
            int red = triangle.puddle ? 58 : 24;
            int green = triangle.puddle ? 78 : 34;
            int blue = triangle.puddle ? 96 : 39;
            int alpha = (int) (255.0F * (triangle.puddle
                    ? 0.07F + triangle.strength * 0.16F
                    : 0.035F + triangle.strength * 0.085F));
            float y = triangle.y + (triangle.puddle ? 0.0025F : 0.0012F);
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
        cachedLevel = level;
        cachedX = centerX;
        cachedZ = centerZ;
        cachedTick = tick;
        TRIANGLES.clear();

        int radius = settings.surfaceOverlayRadiusBlocks();
        int maximumCells = settings.maximumSurfacePatches();
        int minimumX = centerX - radius - 1;
        int minimumZ = centerZ - radius - 1;
        int diameter = radius * 2 + 3;
        int[] heights = sampleHeights(level, minimumX, minimumZ, diameter);
        int wetCells = 0;
        int puddleCells = 0;
        int surfaceCells = 0;
        BlockPos.MutableBlockPos ground = new BlockPos.MutableBlockPos();
        BlockPos.MutableBlockPos sky = new BlockPos.MutableBlockPos();
        // Center-out square rings keep a reduced patch budget distributed around
        // the camera instead of cutting off one side in scan-line order.
        for (int ring = 0; ring <= radius && surfaceCells < maximumCells; ring++) {
            for (int deltaZ = -ring; deltaZ <= ring && surfaceCells < maximumCells; deltaZ++) {
                for (int deltaX = -ring; deltaX <= ring && surfaceCells < maximumCells; deltaX++) {
                    if (Math.max(Math.abs(deltaX), Math.abs(deltaZ)) != ring) {
                        continue;
                    }
                    int x = centerX + deltaX;
                    int z = centerZ + deltaZ;
                    if ((long) deltaX * deltaX + (long) deltaZ * deltaZ > (long) radius * radius) {
                        continue;
                    }
                    int localX = x - minimumX;
                    int localZ = z - minimumZ;
                    int y = heightAt(heights, diameter, localX, localZ);
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
                    int north = heightAt(heights, diameter, localX, localZ - 1);
                    int east = heightAt(heights, diameter, localX + 1, localZ);
                    int south = heightAt(heights, diameter, localX, localZ + 1);
                    int west = heightAt(heights, diameter, localX - 1, localZ);
                    boolean wetSuitable = surface.wetness() >= 0.08D
                            && SurfacePatchModel.flatEnough(y, north, east, south, west, 1);
                    boolean puddleSuitable = surface.puddleCoverage() >= 0.04D
                            && SurfacePatchModel.flatEnough(y, north, east, south, west, 0);
                    boolean added = false;
                    if (wetSuitable) {
                        int before = TRIANGLES.size();
                        appendContour(x, y, z, surface.wetness(), false, WET_SALT);
                        if (TRIANGLES.size() > before) {
                            wetCells++;
                            added = true;
                        }
                    }
                    if (puddleSuitable) {
                        int before = TRIANGLES.size();
                        appendContour(x, y, z, surface.puddleCoverage(), true, PUDDLE_SALT);
                        if (TRIANGLES.size() > before) {
                            puddleCells++;
                            added = true;
                        }
                    }
                    if (added) {
                        surfaceCells++;
                    }
                }
            }
        }
        diagnostics = new Diagnostics(true, wetCells, puddleCells, TRIANGLES.size());
    }

    private static int[] sampleHeights(ClientLevel level, int minimumX, int minimumZ, int diameter) {
        int[] heights = new int[diameter * diameter];
        BlockPos.MutableBlockPos probe = new BlockPos.MutableBlockPos();
        for (int localZ = 0; localZ < diameter; localZ++) {
            for (int localX = 0; localX < diameter; localX++) {
                int x = minimumX + localX;
                int z = minimumZ + localZ;
                probe.set(x, 64, z);
                heights[localZ * diameter + localX] = level.hasChunkAt(probe)
                        ? level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z)
                        : Integer.MIN_VALUE;
            }
        }
        return heights;
    }

    private static int heightAt(int[] heights, int diameter, int x, int z) {
        if (x < 0 || z < 0 || x >= diameter || z >= diameter) {
            return Integer.MIN_VALUE;
        }
        return heights[z * diameter + x];
    }

    private static void appendContour(
            int blockX,
            int y,
            int blockZ,
            double coverage,
            boolean puddle,
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
                    puddle
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
            boolean puddle
    ) {
    }

    /** Renderer facts kept separate from synchronized surface state. */
    public record Diagnostics(boolean active, int wetCells, int puddleCells, int triangles) {
        public static final Diagnostics INACTIVE = new Diagnostics(false, 0, 0, 0);
    }
}
