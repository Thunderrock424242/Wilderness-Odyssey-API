package com.thunder.wildernessodysseyapi.SkyBeam;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import net.minecraft.core.BlockPos;

public class SkyBeamRenderer {

    private static final ResourceLocation BEAM_TEXTURE = ResourceLocation.tryParse("textures/entity/beacon_beam.png");

    public static void renderBeam(PoseStack poseStack, MultiBufferSource buffer,
                                  Vec3 cam, SkyBeamData beam, long gameTime) {

        BlockPos pos = beam.pos();
        double x = pos.getX() - cam.x();
        double y = pos.getY() - cam.y();
        double z = pos.getZ() - cam.z();

        float alpha = 0.7F;
        float width = 0.75F;
        float height = 256.0F;

        float r = ((beam.colorRGB() >> 16) & 0xFF) / 255.0F;
        float g = ((beam.colorRGB() >> 8) & 0xFF) / 255.0F;
        float b = (beam.colorRGB() & 0xFF) / 255.0F;

        VertexConsumer vc = buffer.getBuffer(RenderType.beaconBeam(BEAM_TEXTURE, true));

        poseStack.pushPose();
        poseStack.translate(x + 0.5, y, z + 0.5);

        float scrollSpeed = 0.02f;
        float vScroll = (gameTime % 200) * scrollSpeed;

        for (float yOffset = 0; yOffset < height; yOffset += 10f) {
            float y1 = yOffset;
            float y2 = yOffset + 10f;
            float v1 = y1 / height + vScroll;
            float v2 = y2 / height + vScroll;

            vc.addVertex(-width, y1, 0.0f).setColor(r, g, b, alpha).setUv(0.0f, v1).setUv1(0, 0).setUv2(0, 240).setNormal(0.0f, 1.0f, 0.0f);
            vc.addVertex(width, y1, 0.0f).setColor(r, g, b, alpha).setUv(1.0f, v1).setUv1(0, 0).setUv2(0, 240).setNormal(0.0f, 1.0f, 0.0f);
            vc.addVertex(width, y2, 0.0f).setColor(r, g, b, alpha).setUv(1.0f, v2).setUv1(0, 0).setUv2(0, 240).setNormal(0.0f, 1.0f, 0.0f);
            vc.addVertex(-width, y2, 0.0f).setColor(r, g, b, alpha).setUv(0.0f, v2).setUv1(0, 0).setUv2(0, 240).setNormal(0.0f, 1.0f, 0.0f);
        }

        poseStack.popPose();
    }
}
