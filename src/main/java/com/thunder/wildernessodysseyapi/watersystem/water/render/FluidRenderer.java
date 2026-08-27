package com.thunder.wildernessodysseyapi.watersystem.water.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.thunder.wildernessodysseyapi.gpuprofiler.client.GpuDiagnostics;
import com.thunder.wildernessodysseyapi.watersystem.water.mesh.FluidMesh;
import com.thunder.wildernessodysseyapi.watersystem.water.config.WildernessWaterRules;
import com.thunder.wildernessodysseyapi.watersystem.water.sph.SPHConstants;
import com.thunder.wildernessodysseyapi.watersystem.water.sph.SPHSimulationManager;
import com.thunder.wildernessodysseyapi.watersystem.water.sph.SPHSimulator;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.client.extensions.common.IClientFluidTypeExtensions;
import net.neoforged.neoforge.client.textures.FluidSpriteCache;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Renders the transient SPH water mesh through Minecraft's normal translucent
 * terrain render type. That keeps the geometry friendly to Sodium/Iris and
 * avoids custom shader state fighting with shader packs.
 */
public class FluidRenderer {

    private static final Map<SPHSimulator, FluidMesh> meshMap = new ConcurrentHashMap<>();
    private static final List<SPHSimulator> ACTIVE_SIMULATIONS = new ArrayList<>(
            SPHConstants.MAX_ACTIVE_SIMULATIONS + SPHConstants.MAX_TRANSIENT_SHORE_SIMULATIONS
    );
    private static final List<SPHSimulator> RENDER_SIMULATIONS = new ArrayList<>(
            SPHConstants.MAX_ACTIVE_SIMULATIONS + SPHConstants.MAX_TRANSIENT_SHORE_SIMULATIONS
    );
    private static final FluidState WATER_STATE = Fluids.WATER.defaultFluidState();
    private static int meshRebuildCursor;

    private static final float WATER_ALPHA = 0.56f;
    private static final float DROPLET_ALPHA = 0.62f;
    private static final float TEXTURE_SCALE = 0.45f;

    private static final float BASE_R = 0.46f;
    private static final float BASE_G = 0.76f;
    private static final float BASE_B = 1.00f;

    public static void onRenderLevel(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) return;
        try (GpuDiagnostics.Scope ignored = GpuDiagnostics.scope("water.sph")) {
            renderScoped(event);
        }
    }

    private static void renderScoped(RenderLevelStageEvent event) {
        if (!WaterRenderingConfig.ENABLE_SPH_WATER_RENDERING.get()) {
            clear();
            return;
        }

        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (level == null) return;
        if (!WildernessWaterRules.isEnabled(level)) {
            clear();
            return;
        }

        SPHSimulationManager manager = SPHSimulationManager.get();
        ACTIVE_SIMULATIONS.clear();
        manager.collectActive(level, ACTIVE_SIMULATIONS);

        if (ACTIVE_SIMULATIONS.isEmpty()) {
            clear();
            return;
        }

        meshMap.keySet().retainAll(ACTIVE_SIMULATIONS);
        ACTIVE_SIMULATIONS.sort(Comparator.comparingDouble(sim -> distanceSquaredTo(sim, event.getCamera().getPosition().x, event.getCamera().getPosition().y, event.getCamera().getPosition().z)));

        PoseStack poseStack = event.getPoseStack();
        var camera = event.getCamera().getPosition();
        TextureAtlasSprite waterSprite = FluidSpriteCache.getFluidSprites(level, BlockPos.containing(camera), WATER_STATE)[0];
        var bufferSource = mc.renderBuffers().bufferSource();
        VertexConsumer buffer = bufferSource.getBuffer(RenderType.translucent());

        poseStack.pushPose();
        poseStack.translate(-camera.x, -camera.y, -camera.z);

        int maxRenderedSimulations = WaterRenderingConfig.maxRenderedSphSimulations();
        int meshRevisionInterval = WaterRenderingConfig.sphMeshRevisionInterval();
        RENDER_SIMULATIONS.clear();
        for (SPHSimulator sim : ACTIVE_SIMULATIONS) {
            if (RENDER_SIMULATIONS.size() >= maxRenderedSimulations) {
                break;
            }
            if (isNearCamera(sim, camera.x, camera.y, camera.z)) {
                RENDER_SIMULATIONS.add(sim);
            }
        }
        rebuildPendingMeshes(meshRevisionInterval);

        boolean drew = false;
        for (SPHSimulator sim : RENDER_SIMULATIONS) {
            FluidMesh mesh = meshMap.get(sim);
            if (mesh != null && mesh.hasGeometry()) {
                int tint = waterTint(level, sim.getCenterX(), sim.getCenterY(), sim.getCenterZ());
                int light = waterLight(level, sim.getCenterX(), sim.getCenterY(), sim.getCenterZ());
                drawFluidMesh(mesh, poseStack.last(), buffer, waterSprite, tint, light);
                drew = true;
            }

            drew |= drawDroplets(sim, poseStack.last(), buffer, waterSprite, level);
        }

        poseStack.popPose();

        if (drew) {
            // WaterRenderCoordinator flushes the shared translucent detail batch.
        }
    }

    /** Releases retained SPH mesh arrays when rendering or the client level stops. */
    public static void clear() {
        meshMap.clear();
        ACTIVE_SIMULATIONS.clear();
        RENDER_SIMULATIONS.clear();
        meshRebuildCursor = 0;
    }

    private static boolean isNearCamera(SPHSimulator sim, double cameraX, double cameraY, double cameraZ) {
        int renderDistance = WaterRenderingConfig.sphRenderDistanceBlocks();
        double maxDistanceSquared = renderDistance * renderDistance;
        return distanceSquaredTo(sim, cameraX, cameraY, cameraZ) <= maxDistanceSquared;
    }

    private static double distanceSquaredTo(SPHSimulator sim, double cameraX, double cameraY, double cameraZ) {
        double dx = sim.getCenterX() - cameraX;
        double dy = sim.getCenterY() - cameraY;
        double dz = sim.getCenterZ() - cameraZ;
        return dx * dx + dy * dy + dz * dz;
    }

    /**
     * Spreads synchronous density-field and marching-cubes work across frames.
     * The cursor rotates through the nearest renderable bodies so a continuously
     * changing splash cannot starve other visible simulations.
     */
    private static void rebuildPendingMeshes(int revisionInterval) {
        int simulationCount = RENDER_SIMULATIONS.size();
        int rebuildBudget = WaterRenderingConfig.sphMeshRebuildsPerFrame();
        if (simulationCount == 0 || rebuildBudget <= 0) {
            meshRebuildCursor = 0;
            return;
        }

        int startIndex = Math.floorMod(meshRebuildCursor, simulationCount);
        int examined = 0;
        int rebuilt = 0;
        while (examined < simulationCount && rebuilt < rebuildBudget) {
            SPHSimulator simulator = RENDER_SIMULATIONS.get((startIndex + examined) % simulationCount);
            FluidMesh mesh = meshMap.computeIfAbsent(simulator, FluidMesh::new);
            if (mesh.rebuildIfNeeded(revisionInterval)) {
                rebuilt++;
            }
            examined++;
        }
        meshRebuildCursor = (startIndex + Math.max(1, examined)) % simulationCount;
    }

    private static void drawFluidMesh(
            FluidMesh mesh,
            PoseStack.Pose pose,
            VertexConsumer buffer,
            TextureAtlasSprite sprite,
            int tint,
            int light
    ) {
        float[] data = mesh.meshData;
        if (data == null || data.length < 18) return;

        for (int i = 0; i + 17 < data.length; i += 18) {
            float x0 = data[i],      y0 = data[i + 1],  z0 = data[i + 2];
            float nx0 = data[i + 3], ny0 = data[i + 4], nz0 = data[i + 5];
            float x1 = data[i + 6],  y1 = data[i + 7],  z1 = data[i + 8];
            float nx1 = data[i + 9], ny1 = data[i + 10], nz1 = data[i + 11];
            float x2 = data[i + 12], y2 = data[i + 13], z2 = data[i + 14];
            float nx2 = data[i + 15], ny2 = data[i + 16], nz2 = data[i + 17];

            float nx = (nx0 + nx1 + nx2) / 3.0f;
            float ny = (ny0 + ny1 + ny2) / 3.0f;
            float nz = (nz0 + nz1 + nz2) / 3.0f;
            float len = (float) Math.sqrt(nx * nx + ny * ny + nz * nz);
            if (len > 0.0001f) {
                nx /= len;
                ny /= len;
                nz /= len;
            } else {
                nx = 0.0f;
                ny = 1.0f;
                nz = 0.0f;
            }

            int color = waterColor(tint, nx, ny, nz, WATER_ALPHA);

            emitTriangleAsQuad(
                    buffer, pose, sprite, light, color,
                    x0, y0, z0, nx0, ny0, nz0,
                    x1, y1, z1, nx1, ny1, nz1,
                    x2, y2, z2, nx2, ny2, nz2
            );
            emitTriangleAsQuad(
                    buffer, pose, sprite, light, color,
                    x2, y2, z2, -nx2, -ny2, -nz2,
                    x1, y1, z1, -nx1, -ny1, -nz1,
                    x0, y0, z0, -nx0, -ny0, -nz0
            );
        }
    }

    private static boolean drawDroplets(SPHSimulator sim, PoseStack.Pose pose, VertexConsumer buffer, TextureAtlasSprite sprite, ClientLevel level) {
        float s = 0.055f;
        boolean drew = false;
        for (var p : sim.getRenderParticles()) {
            if (!p.isDroplet) continue;

            float x = p.position.x, y = p.position.y, z = p.position.z;
            float life = Math.max(0.0f, p.dropletLife / (float) SPHConstants.DROPLET_LIFETIME);
            int color = waterColor(level, x, y, z, 0.0f, 1.0f, 0.0f, DROPLET_ALPHA * life);
            int light = waterLight(level, x, y, z);

            emitQuad(buffer, pose, sprite, light, color, 0.0f, 1.0f, 0.0f,
                    x - s, y - s, z,
                    x + s, y - s, z,
                    x + s, y + s, z,
                    x - s, y + s, z);
            drew = true;
        }
        return drew;
    }

    private static void emitTriangleAsQuad(
            VertexConsumer buffer,
            PoseStack.Pose pose,
            TextureAtlasSprite sprite,
            int light,
            int color,
            float x0, float y0, float z0, float nx0, float ny0, float nz0,
            float x1, float y1, float z1, float nx1, float ny1, float nz1,
            float x2, float y2, float z2, float nx2, float ny2, float nz2
    ) {
        addWaterVertex(buffer, pose, sprite, light, color, nx0, ny0, nz0, x0, y0, z0);
        addWaterVertex(buffer, pose, sprite, light, color, nx1, ny1, nz1, x1, y1, z1);
        addWaterVertex(buffer, pose, sprite, light, color, nx2, ny2, nz2, x2, y2, z2);
        addWaterVertex(buffer, pose, sprite, light, color, nx2, ny2, nz2, x2, y2, z2);
    }

    private static void emitQuad(VertexConsumer buffer, PoseStack.Pose pose, TextureAtlasSprite sprite, int light, int color,
                                 float nx, float ny, float nz,
                                 float x0, float y0, float z0,
                                 float x1, float y1, float z1,
                                 float x2, float y2, float z2,
                                 float x3, float y3, float z3) {
        addWaterVertex(buffer, pose, sprite, light, color, nx, ny, nz, x0, y0, z0);
        addWaterVertex(buffer, pose, sprite, light, color, nx, ny, nz, x1, y1, z1);
        addWaterVertex(buffer, pose, sprite, light, color, nx, ny, nz, x2, y2, z2);
        addWaterVertex(buffer, pose, sprite, light, color, nx, ny, nz, x3, y3, z3);
    }

    private static void addWaterVertex(VertexConsumer buffer, PoseStack.Pose pose, TextureAtlasSprite sprite, int light, int color,
                                       float nx, float ny, float nz, float x, float y, float z) {
        float u = waterU(sprite, x, y, z, nx, ny, nz);
        float v = waterV(sprite, x, y, z, nx, ny, nz);
        buffer.addVertex(pose, x, y, z)
                .setColor((color >> 16) & 0xFF, (color >> 8) & 0xFF, color & 0xFF, (color >>> 24) & 0xFF)
                .setUv(u, v)
                .setLight(light)
                .setNormal(pose, nx, ny, nz);
    }

    private static float waterU(TextureAtlasSprite sprite, float x, float y, float z, float nx, float ny, float nz) {
        return sprite.getU(tile(projectU(x, y, z, nx, ny, nz)));
    }

    private static float waterV(TextureAtlasSprite sprite, float x, float y, float z, float nx, float ny, float nz) {
        return sprite.getV(tile(projectV(x, y, z, nx, ny, nz)));
    }

    private static float projectU(float x, float y, float z, float nx, float ny, float nz) {
        float ax = Math.abs(nx);
        float ay = Math.abs(ny);
        float az = Math.abs(nz);

        if (ay >= ax && ay >= az) {
            return x * TEXTURE_SCALE;
        }
        return (ax >= az ? z : x) * TEXTURE_SCALE;
    }

    private static float projectV(float x, float y, float z, float nx, float ny, float nz) {
        float ax = Math.abs(nx);
        float ay = Math.abs(ny);
        float az = Math.abs(nz);

        if (ay >= ax && ay >= az) {
            return z * TEXTURE_SCALE;
        }
        return y * TEXTURE_SCALE;
    }

    private static int waterLight(ClientLevel level, float x, float y, float z) {
        BlockPos pos = BlockPos.containing(x, y, z);
        if (!level.hasChunkAt(pos)) return LightTexture.FULL_BRIGHT;

        int packed = LevelRenderer.getLightColor(level, pos);
        int block = Math.max(7, LightTexture.block(packed));
        int sky = Math.max(7, LightTexture.sky(packed));
        return LightTexture.pack(block, sky);
    }

    private static int waterColor(ClientLevel level, float x, float y, float z, float nx, float ny, float nz, float alpha) {
        return waterColor(waterTint(level, x, y, z), nx, ny, nz, alpha);
    }

    private static int waterTint(ClientLevel level, float x, float y, float z) {
        BlockPos pos = BlockPos.containing(x, y, z);
        return IClientFluidTypeExtensions.of(Fluids.WATER).getTintColor(WATER_STATE, level, pos);
    }

    private static int waterColor(int tint, float nx, float ny, float nz, float alpha) {
        float tr = ((tint >> 16) & 0xFF) / 255.0f;
        float tg = ((tint >> 8) & 0xFF) / 255.0f;
        float tb = (tint & 0xFF) / 255.0f;

        float shade = 0.88f + 0.16f * Math.abs(ny) + 0.04f * Math.max(0.0f, nx * -0.35f + nz * 0.45f);
        int r = channel((BASE_R * 0.68f + tr * 0.32f) * shade);
        int g = channel((BASE_G * 0.68f + tg * 0.32f) * shade);
        int b = channel((BASE_B * 0.68f + tb * 0.32f) * shade);
        int a = channel(alpha);
        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    private static int channel(float value) {
        return Math.max(0, Math.min(255, Math.round(value * 255.0f)));
    }

    private static float tile(float value) {
        return value - (float) Math.floor(value);
    }
}
