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
import net.minecraft.world.level.levelgen.Heightmap;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;

import java.util.ArrayList;
import java.util.List;

/** Draws bounded cosmetic wetness and puddles from synchronized surface memory. */
public final class WeatherSurfaceRenderer {
    private static final List<Patch> PATCHES = new ArrayList<>();
    private static ClientLevel cachedLevel;
    private static int cachedX = Integer.MIN_VALUE;
    private static int cachedZ = Integer.MIN_VALUE;
    private static long cachedTick = Long.MIN_VALUE;

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
        if (PATCHES.isEmpty()) {
            return;
        }

        var buffers = minecraft.renderBuffers().bufferSource();
        var renderType = WeatherSurfaceRenderTypes.wetSurface();
        VertexConsumer vertices = buffers.getBuffer(renderType);
        PoseStack poses = event.getPoseStack();
        poses.pushPose();
        poses.translate(-camera.x, -camera.y, -camera.z);
        var matrix = poses.last().pose();
        for (Patch patch : PATCHES) {
            int red = patch.puddle ? 46 : 38;
            int green = patch.puddle ? 70 : 54;
            int blue = patch.puddle ? 88 : 61;
            int alpha = (int) (255.0 * (patch.puddle
                    ? 0.12 + patch.strength * 0.18
                    : 0.07 + patch.strength * 0.10));
            float half = patch.puddle ? 0.76F : 0.57F;
            float y = patch.y + 0.008F;
            vertices.addVertex(matrix, patch.x - half, y, patch.z + half).setColor(red, green, blue, alpha);
            vertices.addVertex(matrix, patch.x + half, y, patch.z + half).setColor(red, green, blue, alpha);
            vertices.addVertex(matrix, patch.x + half, y, patch.z - half).setColor(red, green, blue, alpha);
            vertices.addVertex(matrix, patch.x - half, y, patch.z - half).setColor(red, green, blue, alpha);
        }
        poses.popPose();
        buffers.endBatch(renderType);
    }

    /** Clears cached columns on disconnect and dimension changes. */
    public static void clear() {
        PATCHES.clear();
        cachedLevel = null;
        cachedX = Integer.MIN_VALUE;
        cachedZ = Integer.MIN_VALUE;
        cachedTick = Long.MIN_VALUE;
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
        PATCHES.clear();
        int radius = settings.surfaceOverlayRadiusBlocks();
        int maximum = settings.maximumSurfacePatches();
        for (int z = centerZ - radius; z <= centerZ + radius && PATCHES.size() < maximum; z += 2) {
            for (int x = centerX - radius; x <= centerX + radius && PATCHES.size() < maximum; x += 2) {
                if ((x - centerX) * (x - centerX) + (z - centerZ) * (z - centerZ) > radius * radius) {
                    continue;
                }
                BlockPos probe = new BlockPos(x, 64, z);
                if (!level.hasChunkAt(probe)) {
                    continue;
                }
                int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
                BlockPos ground = new BlockPos(x, y - 1, z);
                if (!level.getFluidState(ground).isEmpty()
                        || !level.getBlockState(ground).isSolidRender(level, ground)) {
                    continue;
                }
                WeatherSample sample = ClientWeatherCoordinator.sampleAt(level, new BlockPos(x, y, z));
                SurfaceWeatherState surface = sample.surface();
                if (surface.wetness() < 0.12) {
                    continue;
                }
                double noise = noise(x, z, 0x9E3779B9);
                if (noise > surface.wetness()) {
                    continue;
                }
                boolean puddle = noise(x, z, 0xC2B2AE35) < surface.puddleCoverage();
                PATCHES.add(new Patch(x + 0.5F, y, z + 0.5F,
                        (float) (puddle ? surface.puddleCoverage() : surface.wetness()), puddle));
            }
        }
    }

    private static double noise(int x, int z, int salt) {
        int value = x * 734_287_067 ^ z * 912_931 ^ salt;
        value ^= value >>> 13;
        value *= 1_274_126_177;
        return (value & 0x7FFFFFFF) / (double) Integer.MAX_VALUE;
    }

    private record Patch(float x, float y, float z, float strength, boolean puddle) {
    }
}
