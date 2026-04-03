package com.thunder.wildernessodysseyapi.watersystem.volumetric.client;

import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.thunder.wildernessodysseyapi.core.ModConstants;
import com.thunder.wildernessodysseyapi.watersystem.volumetric.VolumetricFluidManager.SimulatedFluid;
import com.thunder.wildernessodysseyapi.watersystem.volumetric.VolumetricSurfaceMesher;
import com.thunder.wildernessodysseyapi.watersystem.volumetric.VolumetricSurfaceMesher.Triangle;
import com.thunder.wildernessodysseyapi.watersystem.volumetric.VolumetricSurfaceMesher.Vertex;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BufferUploader;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.joml.Matrix4f;

import java.util.List;

/**
 * First-pass client preview renderer for synced volumetric fluid surfaces.
 */
@EventBusSubscriber(modid = ModConstants.MOD_ID, value = Dist.CLIENT, bus = EventBusSubscriber.Bus.GAME)
public final class VolumetricSurfaceRenderer {
    private static final double MAX_EDGE_DELTA = 1.5D;

    private VolumetricSurfaceRenderer() {
    }

    @SubscribeEvent
    public static void onRenderLevelStage(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_LEVEL) {
            return;
        }

        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null || minecraft.player == null) {
            return;
        }
        if (!VolumetricFluidRenderConfig.ENABLE_PREVIEW_RENDERER.get()) {
            return;
        }

        PoseStack poseStack = event.getPoseStack();
        Vec3 camera = event.getCamera().getPosition();
        Matrix4f pose = poseStack.last().pose();

        double time = minecraft.level.getGameTime();
        int maxTrianglesPerFluid = VolumetricFluidRenderConfig.MAX_TRIANGLES_PER_FLUID.get();
        int maxRenderDistance = VolumetricFluidRenderConfig.MAX_RENDER_DISTANCE.get();
        double maxRenderDistanceSq = maxRenderDistance * (double) maxRenderDistance;
        double waveStrength = VolumetricFluidRenderConfig.WAVE_STRENGTH.get();
        double foamStrength = VolumetricFluidRenderConfig.FOAM_STRENGTH.get();

        if (VolumetricSurfaceClientCache.isStale((long) time, VolumetricFluidRenderConfig.MAX_STALE_AGE_TICKS.get())) {
            return;
        }

        renderFluidPass(
                VolumetricSurfaceMesher.buildTriangles(
                        VolumetricSurfaceClientCache.snapshot(minecraft.level.dimension().location(), SimulatedFluid.WATER), MAX_EDGE_DELTA),
                VolumetricSurfaceRenderTypes.waterSurface(),
                pose,
                camera,
                time,
                maxTrianglesPerFluid,
                maxRenderDistanceSq,
                waveStrength,
                foamStrength,
                55,
                130,
                220,
                VolumetricFluidRenderConfig.WATER_ALPHA.get()
        );

        renderFluidPass(
                VolumetricSurfaceMesher.buildTriangles(
                        VolumetricSurfaceClientCache.snapshot(minecraft.level.dimension().location(), SimulatedFluid.LAVA), MAX_EDGE_DELTA),
                VolumetricSurfaceRenderTypes.lavaSurface(),
                pose,
                camera,
                time,
                maxTrianglesPerFluid,
                maxRenderDistanceSq,
                waveStrength,
                foamStrength,
                255,
                120,
                40,
                VolumetricFluidRenderConfig.LAVA_ALPHA.get()
        );
    }

    private static void renderFluidPass(List<Triangle> triangles,
                                        RenderType renderType,
                                        Matrix4f pose,
                                        Vec3 camera,
                                        double time,
                                        int maxTrianglesPerFluid,
                                        double maxRenderDistanceSq,
                                        double waveStrength,
                                        double foamStrength,
                                        int red,
                                        int green,
                                        int blue,
                                        int alpha) {
        BufferBuilder bufferBuilder = Tesselator.getInstance().begin(VertexFormat.Mode.TRIANGLES, DefaultVertexFormat.POSITION_COLOR_TEX_LIGHTMAP);
        int vertexCount = renderFluid(triangles, bufferBuilder, pose, camera, time, maxTrianglesPerFluid, maxRenderDistanceSq, waveStrength, foamStrength, red, green, blue, alpha);
        drawIfPopulated(renderType, bufferBuilder, vertexCount);
    }

    private static int renderFluid(List<Triangle> triangles,
                                   VertexConsumer consumer,
                                   Matrix4f pose,
                                   Vec3 camera,
                                   double time,
                                   int maxTrianglesPerFluid,
                                   double maxRenderDistanceSq,
                                   double waveStrength,
                                   double foamStrength,
                                   int red,
                                   int green,
                                   int blue,
                                   int alpha) {
        int rendered = 0;
        int vertices = 0;
        for (Triangle triangle : triangles) {
            if (rendered++ >= maxTrianglesPerFluid) {
                break;
            }
            if (!isTriangleNearCamera(triangle, camera, maxRenderDistanceSq)) {
                continue;
            }
            float foam = foamIntensity(triangle, foamStrength);
            addVertex(consumer, pose, triangle.a(), camera, time, waveStrength, foam, red, green, blue, alpha);
            addVertex(consumer, pose, triangle.b(), camera, time, waveStrength, foam, red, green, blue, alpha);
            addVertex(consumer, pose, triangle.c(), camera, time, waveStrength, foam, red, green, blue, alpha);
            vertices += 3;
        }
        return vertices;
    }

    private static void drawIfPopulated(RenderType renderType, BufferBuilder bufferBuilder, int vertexCount) {
        if (vertexCount <= 0) {
            return;
        }
        renderType.setupRenderState();
        BufferUploader.drawWithShader(bufferBuilder.buildOrThrow());
        renderType.clearRenderState();
    }

    private static boolean isTriangleNearCamera(Triangle triangle, Vec3 camera, double maxRenderDistanceSq) {
        double cx = (triangle.a().x() + triangle.b().x() + triangle.c().x()) / 3.0D;
        double cy = (triangle.a().y() + triangle.b().y() + triangle.c().y()) / 3.0D;
        double cz = (triangle.a().z() + triangle.b().z() + triangle.c().z()) / 3.0D;
        double dx = cx - camera.x;
        double dy = cy - camera.y;
        double dz = cz - camera.z;
        return (dx * dx + dy * dy + dz * dz) <= maxRenderDistanceSq;
    }

    private static void addVertex(VertexConsumer consumer,
                                  Matrix4f pose,
                                  Vertex vertex,
                                  Vec3 camera,
                                  double time,
                                  double waveStrength,
                                  float foam,
                                  int red,
                                  int green,
                                  int blue,
                                  int alpha) {
        float x = (float) (vertex.x() - camera.x);
        float z = (float) (vertex.z() - camera.z);
        double beachBoost = 1.0D + (vertex.shorelineFactor() * 1.25D);
        double moonBoost = Math.max(0.75D, Math.min(1.3D, vertex.moonPhaseFactor()));
        float wave = (float) (Math.sin((vertex.x() * 0.65D) + (vertex.z() * 0.35D) + (time * 0.07D))
                * waveStrength * beachBoost * moonBoost);
        float y = (float) (vertex.y() - camera.y + wave);
        int shadedRed = shadeWithFoam(red, foam);
        int shadedGreen = shadeWithFoam(green, foam);
        int shadedBlue = shadeWithFoam(blue, foam);

        consumer.addVertex(pose, x, y, z)
                .setColor(shadedRed, shadedGreen, shadedBlue, alpha)
                .setUv(0.0F, 0.0F)
                .setLight(0x00F000F0);
    }

    private static int shadeWithFoam(int baseChannel, float foam) {
        float blended = (baseChannel / 255.0F) * (1.0F - foam) + foam;
        return Math.max(0, Math.min(255, Math.round(blended * 255.0F)));
    }

    private static float foamIntensity(Triangle triangle, double foamStrength) {
        double ux = triangle.b().x() - triangle.a().x();
        double uy = triangle.b().y() - triangle.a().y();
        double uz = triangle.b().z() - triangle.a().z();

        double vx = triangle.c().x() - triangle.a().x();
        double vy = triangle.c().y() - triangle.a().y();
        double vz = triangle.c().z() - triangle.a().z();

        double nx = uy * vz - uz * vy;
        double ny = uz * vx - ux * vz;
        double nz = ux * vy - uy * vx;

        double len = Math.sqrt(nx * nx + ny * ny + nz * nz);
        if (len <= 1.0E-6D) {
            return 0.0F;
        }
        double upward = Math.abs(ny / len);
        double steepness = 1.0D - upward;
        double shoreline = (triangle.a().shorelineFactor() + triangle.b().shorelineFactor() + triangle.c().shorelineFactor()) / 3.0D;
        double foam = (steepness * foamStrength) + (shoreline * 0.25D);
        return (float) Math.max(0.0D, Math.min(1.0D, foam));
    }
}
