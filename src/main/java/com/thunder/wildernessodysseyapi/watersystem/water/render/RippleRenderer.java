package com.thunder.wildernessodysseyapi.watersystem.water.render;

import net.minecraft.client.renderer.RenderType;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import java.util.*;

/**
 * RippleRenderer
 *
 * Renders expanding animated ripple rings on the water surface.
 * Each ripple is a flat circle quad that grows in radius and fades in alpha.
 *
 * Ripples are spawned from WaterEntryEventHandler when an entity enters water.
 * They are drawn during RenderLevelStageEvent AFTER_TRANSLUCENT_BLOCKS so they
 * composite correctly with the translucent water geometry.
 */
@EventBusSubscriber(modid = "wilderness", bus = EventBusSubscriber.Bus.GAME, value = Dist.CLIENT)
public class RippleRenderer {

    private static final float MAX_RADIUS = 1.8f;
    private static final float EXPAND_SPEED = 0.06f;  // radius per frame
    private static final float FADE_START = 0.6f;     // fraction of life when fading begins
    private static final int   RING_SEGMENTS = 24;

    private static final List<Ripple> activeRipples = new ArrayList<>();

    public static void spawnRipple(double x, double y, double z) {
        activeRipples.add(new Ripple(x, y, z));
    }

    @SubscribeEvent
    public static void onRenderLevel(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) return;
        if (activeRipples.isEmpty()) return;

        PoseStack poseStack = event.getPoseStack();
        Vec3 camera = event.getCamera().getPosition();

        // Advance + cull dead ripples
        activeRipples.removeIf(r -> r.radius >= MAX_RADIUS);

        var bufferSource = net.minecraft.client.Minecraft.getInstance()
                .renderBuffers().bufferSource();
        VertexConsumer buffer = bufferSource.getBuffer(RenderType.translucent());

        poseStack.pushPose();

        for (Ripple ripple : activeRipples) {
            ripple.radius += EXPAND_SPEED;

            float life = ripple.radius / MAX_RADIUS;               // 0 → 1
            float alpha = life < FADE_START
                    ? 0.6f
                    : 0.6f * (1f - (life - FADE_START) / (1f - FADE_START));

            int alphaByte = (int)(alpha * 255);
            if (alphaByte < 4) continue;

            double rx = ripple.x - camera.x;
            double ry = ripple.y - camera.y + 0.02; // slight Y offset above surface
            double rz = ripple.z - camera.z;

            poseStack.pushPose();
            poseStack.translate(rx, ry, rz);

            drawRing(buffer, poseStack, ripple.radius, alphaByte);

            poseStack.popPose();
        }

        poseStack.popPose();
        bufferSource.endBatch(RenderType.translucent());
    }

    private static void drawRing(VertexConsumer buffer, PoseStack poseStack,
                                  float radius, int alpha) {
        var matrix = poseStack.last().pose();
        float innerRadius = radius * 0.82f;

        for (int i = 0; i < RING_SEGMENTS; i++) {
            double a0 = (2 * Math.PI * i)       / RING_SEGMENTS;
            double a1 = (2 * Math.PI * (i + 1)) / RING_SEGMENTS;

            float ox0 = (float) Math.cos(a0), oz0 = (float) Math.sin(a0);
            float ox1 = (float) Math.cos(a1), oz1 = (float) Math.sin(a1);

            // Outer quad strip (2 triangles per segment as a quad)
            addRippleVertex(buffer, matrix, ox0 * radius,       0, oz0 * radius,       alpha);
            addRippleVertex(buffer, matrix, ox0 * innerRadius,  0, oz0 * innerRadius,  alpha);
            addRippleVertex(buffer, matrix, ox1 * innerRadius,  0, oz1 * innerRadius,  alpha);
            addRippleVertex(buffer, matrix, ox1 * radius,       0, oz1 * radius,       alpha);
        }
    }

    private static void addRippleVertex(VertexConsumer buffer,
                                         org.joml.Matrix4f matrix,
                                         float x, float y, float z,
                                         int alpha) {
        buffer.addVertex(matrix, x, y, z)
              .setColor(180, 220, 255, alpha)
              .setUv(0, 0)
              .setUv2(240, 0)
              .setNormal(0, 1, 0);
    }

    // ---- Inner data class ----

    private static class Ripple {
        final double x, y, z;
        float radius = 0.1f;

        Ripple(double x, double y, double z) {
            this.x = x;
            this.y = y;
            this.z = z;
        }
    }
}
