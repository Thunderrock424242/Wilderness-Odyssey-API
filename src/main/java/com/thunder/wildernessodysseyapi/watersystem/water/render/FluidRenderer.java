package com.thunder.wildernessodysseyapi.watersystem.water.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.thunder.wildernessodysseyapi.watersystem.water.mesh.FluidMesh;
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
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Renders the transient SPH water mesh through Minecraft's normal translucent
 * terrain render type. That keeps the geometry friendly to Sodium/Iris and
 * avoids custom shader state fighting with shader packs.
 */
@EventBusSubscriber(modid = "wildernessodysseyapi", value = Dist.CLIENT)
public class FluidRenderer {

    private static final Map<SPHSimulator, FluidMesh> meshMap = new ConcurrentHashMap<>();
    private static final FluidState WATER_STATE = Fluids.WATER.defaultFluidState();

    private static final float WATER_ALPHA = 0.56f;
    private static final float DROPLET_ALPHA = 0.62f;
    private static final float TEXTURE_SCALE = 0.45f;

    private static final float BASE_R = 0.46f;
    private static final float BASE_G = 0.76f;
    private static final float BASE_B = 1.00f;
    private static final float MAX_RENDER_DISTANCE_SQUARED = 128.0f * 128.0f;

    @SubscribeEvent
    public static void onRenderLevel(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) return;

        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (level == null) return;

        List<SPHSimulator> active = new ArrayList<>(SPHSimulationManager.get().getActive(level));
        if (active.isEmpty()) {
            active = new ArrayList<>(SPHSimulationManager.get().getActive());
            if (active.isEmpty()) {
                meshMap.clear();
                return;
            }
        }

        meshMap.keySet().retainAll(active);

        PoseStack poseStack = event.getPoseStack();
        var camera = event.getCamera().getPosition();
        TextureAtlasSprite waterSprite = FluidSpriteCache.getFluidSprites(level, BlockPos.containing(camera), WATER_STATE)[0];
        var bufferSource = mc.renderBuffers().bufferSource();
        VertexConsumer buffer = bufferSource.getBuffer(RenderType.translucent());

        poseStack.pushPose();
        poseStack.translate(-camera.x, -camera.y, -camera.z);

        boolean drew = false;
        for (SPHSimulator sim : active) {
            if (!isNearCamera(sim, camera.x, camera.y, camera.z)) {
                continue;
            }

            FluidMesh mesh = meshMap.computeIfAbsent(sim, FluidMesh::new);
            mesh.rebuild();

            if (mesh.hasGeometry()) {
                drawFluidMesh(mesh, poseStack.last(), buffer, waterSprite, level);
                drew = true;
            }

            drew |= drawDroplets(sim, poseStack.last(), buffer, waterSprite, level);
        }

        poseStack.popPose();

        if (drew) {
            bufferSource.endBatch(RenderType.translucent());
        }
    }

    private static boolean isNearCamera(SPHSimulator sim, double cameraX, double cameraY, double cameraZ) {
        double dx = sim.getCenterX() - cameraX;
        double dy = sim.getCenterY() - cameraY;
        double dz = sim.getCenterZ() - cameraZ;
        return dx * dx + dy * dy + dz * dz <= MAX_RENDER_DISTANCE_SQUARED;
    }

    private static void drawFluidMesh(FluidMesh mesh, PoseStack.Pose pose, VertexConsumer buffer, TextureAtlasSprite sprite, ClientLevel level) {
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

            float cx = (x0 + x1 + x2) / 3.0f;
            float cy = (y0 + y1 + y2) / 3.0f;
            float cz = (z0 + z1 + z2) / 3.0f;
            int color = waterColor(level, cx, cy, cz, nx, ny, nz, WATER_ALPHA);
            int light = waterLight(level, cx, cy, cz);

            emitTriangleAsQuad(buffer, pose, sprite, light, color, nx, ny, nz, x0, y0, z0, x1, y1, z1, x2, y2, z2);
            emitTriangleAsQuad(buffer, pose, sprite, light, color, -nx, -ny, -nz, x2, y2, z2, x1, y1, z1, x0, y0, z0);
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

    private static void emitTriangleAsQuad(VertexConsumer buffer, PoseStack.Pose pose, TextureAtlasSprite sprite, int light, int color,
                                           float nx, float ny, float nz,
                                           float x0, float y0, float z0,
                                           float x1, float y1, float z1,
                                           float x2, float y2, float z2) {
        emitQuad(buffer, pose, sprite, light, color, nx, ny, nz,
                x0, y0, z0,
                x1, y1, z1,
                x2, y2, z2,
                x2, y2, z2);
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
        BlockPos pos = BlockPos.containing(x, y, z);
        int tint = IClientFluidTypeExtensions.of(Fluids.WATER).getTintColor(WATER_STATE, level, pos);
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
