package com.thunder.wildernessodysseyapi.temporalrift.client.hybrid;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import com.thunder.wildernessodysseyapi.temporalrift.blockentity.RiftCoreBlockEntity;
import com.thunder.wildernessodysseyapi.temporalrift.client.TemporalRiftRenderTypes;
import com.thunder.wildernessodysseyapi.temporalrift.hybrid.RiftFieldMath;
import com.thunder.wildernessodysseyapi.temporalrift.hybrid.RiftState;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;

/** Layered world-space tear. Independent geometry motion provides a fallback without post-processing. */
public final class RiftVortexRenderer {
    private static final int SEGMENTS = 32;
    private RiftVortexRenderer() { }

    public static void render(RiftCoreBlockEntity core, float partial, PoseStack poses, MultiBufferSource buffers) {
        double time = (core.getLevel() == null ? 0 : core.getLevel().getGameTime() % 24000) + partial;
        float pulse = RiftClientEffects.enabled() && !RiftClientEffects.reduced()
                ? (float) RiftFieldMath.pulse(time, core.getBlockPos().asLong()) : 0;
        float power = (float) core.getRiftState().visualIntensity();
        poses.pushPose();
        poses.translate(0.5, 2.5, 0.5);
        poses.mulPose(Axis.YP.rotationDegrees(-core.getFacing().toYRot() + 180));
        var depth = buffers.getBuffer(RenderType.debugQuads());
        for (int i = 0; i < SEGMENTS; i++) {
            float a = (float) (i * Math.PI * 2 / SEGMENTS);
            float b = (float) ((i + 1) * Math.PI * 2 / SEGMENTS);
            float ax = (float) Math.cos(a) * 1.46F, ay = (float) Math.sin(a) * 1.46F;
            float bx = (float) Math.cos(b) * 1.46F, by = (float) Math.sin(b) * 1.46F;
            plainQuad(depth, poses, 0, 0, -0.20F, ax, ay, -0.20F, bx, by, -0.20F, 0, 0, -0.20F);
            plainQuad(depth, poses, 0, 0, 0.20F, bx, by, 0.20F, ax, ay, 0.20F, 0, 0, 0.20F);
            plainQuad(depth, poses, ax, ay, -0.2F, ax, ay, 0.2F, bx, by, 0.2F, bx, by, -0.2F);
        }
        var energy = buffers.getBuffer(TemporalRiftRenderTypes.rift());
        for (int layer = 0; layer < 4; layer++) {
            int red = layer % 2 == 0 ? 28 : 152;
            int green = layer % 2 == 0 ? 205 : 56;
            int blue = 255;
            float phase = (float) time * (layer % 2 == 0 ? 0.018F : -0.012F) + layer * 1.6F;
            for (int i = 0; i < SEGMENTS; i++) {
                float angle = i * (float) Math.PI * 2 / SEGMENTS + phase;
                float next = angle + (float) Math.PI * 2 / SEGMENTS;
                float radius = 0.34F + layer * 0.32F + (float) Math.sin(angle * 3 + time * 0.035) * 0.04F;
                float width = (0.015F + power * 0.025F) * (1 + pulse * 0.3F);
                float z = layer % 2 == 0 ? -0.23F - layer * 0.04F : 0.23F + layer * 0.04F;
                ribbon(energy, poses, angle, next, radius, width, z, red, green, blue, 85 + (int) (power * 70));
            }
        }
        if (core.getRiftState() == RiftState.CRITICAL && pulse > 0.9F && !RiftClientEffects.reduced()) {
            ribbon(energy, poses, (float) time * 0.01F, (float) time * 0.01F + 0.55F, 1.37F, 0.025F, -0.36F, 190, 48, 64, 100);
        }
        // Brief segmented arcs cross the rim rather than flashing the whole screen.
        if (pulse > 0.92F) {
            for (int i = 0; i < 5; i++) {
                float start = i * 0.14F + (float) time * 0.003F;
                ribbon(energy, poses, start, start + 0.13F, 1.42F + (i % 2) * 0.09F, 0.012F, -0.4F, 110, 235, 255, 170);
            }
        }
        poses.popPose();
    }

    private static void ribbon(VertexConsumer buffer, PoseStack poses, float a, float b, float radius, float width, float z,
                               int red, int green, int blue, int alpha) {
        energyVertex(buffer, poses, a, radius - width, z, red, green, blue, alpha);
        energyVertex(buffer, poses, b, radius - width, z, red, green, blue, alpha);
        energyVertex(buffer, poses, b, radius + width, z, red, green, blue, alpha);
        energyVertex(buffer, poses, a, radius + width, z, red, green, blue, alpha);
    }

    private static void energyVertex(VertexConsumer buffer, PoseStack poses, float angle, float radius, float z, int r, int g, int b, int a) {
        buffer.addVertex(poses.last(), (float) Math.cos(angle) * radius, (float) Math.sin(angle) * radius, z)
                .setColor(r, g, b, a).setNormal(poses.last(), 0, 0, 1);
    }

    private static void plainQuad(VertexConsumer buffer, PoseStack poses, float x0, float y0, float z0, float x1, float y1, float z1,
                                  float x2, float y2, float z2, float x3, float y3, float z3) {
        buffer.addVertex(poses.last(), x0, y0, z0).setColor(2, 1, 6, 255);
        buffer.addVertex(poses.last(), x1, y1, z1).setColor(2, 1, 6, 255);
        buffer.addVertex(poses.last(), x2, y2, z2).setColor(2, 1, 6, 255);
        buffer.addVertex(poses.last(), x3, y3, z3).setColor(2, 1, 6, 255);
    }
}
