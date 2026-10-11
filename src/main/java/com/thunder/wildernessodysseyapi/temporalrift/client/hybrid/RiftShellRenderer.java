package com.thunder.wildernessodysseyapi.temporalrift.client.hybrid;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import com.thunder.wildernessodysseyapi.core.ModConstants;
import com.thunder.wildernessodysseyapi.temporalrift.blockentity.RiftCoreBlockEntity;
import com.thunder.wildernessodysseyapi.temporalrift.hybrid.RiftApertureClip;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import software.bernie.geckolib.cache.object.GeoQuad;
import software.bernie.geckolib.model.GeoModel;
import software.bernie.geckolib.renderer.GeoBlockRenderer;
import software.bernie.geckolib.renderer.layer.AutoGlowingGeoLayer;

import java.util.ArrayList;

/** Supplied Blockbench shell and animations, with an aperture-only visual guard and fullbright cracks. */
public final class RiftShellRenderer extends GeoBlockRenderer<RiftCoreBlockEntity> {
    private final Matrix4f toAperture = new Matrix4f();
    private final Vector3f transformed = new Vector3f();
    private final Vector3f aperture = new Vector3f();

    public RiftShellRenderer() {
        super(new GeoModel<>() {
            @Override
            public ResourceLocation getModelResource(RiftCoreBlockEntity core) { return id("geo/anomaly_rift_shell.geo.json"); }
            @Override
            public ResourceLocation getTextureResource(RiftCoreBlockEntity core) { return id("textures/block/anomaly_rift_shell.png"); }
            @Override
            public ResourceLocation getAnimationResource(RiftCoreBlockEntity core) { return id("animations/anomaly_rift_shell.animation.json"); }
        });
        withScale(0.8F);
        addRenderLayer(new AutoGlowingGeoLayer<>(this));
    }

    private static ResourceLocation id(String path) { return ResourceLocation.fromNamespaceAndPath(ModConstants.MOD_ID, path); }

    @Override
    protected Direction getFacing(RiftCoreBlockEntity core) { return core.getFacing(); }

    @Override
    public void render(RiftCoreBlockEntity core, float partialTick, PoseStack poses, MultiBufferSource buffers, int light, int overlay) {
        // Input vertices already include the camera pose. Invert that same pose into the unanimated aperture frame.
        poses.pushPose();
        poses.translate(0.5, 2.5, 0.5);
        poses.mulPose(Axis.YP.rotationDegrees(-core.getFacing().toYRot() + 180));
        toAperture.set(poses.last().pose()).invert();
        poses.popPose();
        poses.pushPose();
        // Supplied center bone is at Y=48 pixels. 48/16 * .8 + .1 = 2.5 blocks.
        poses.translate(0, 0.1, 0);
        super.render(core, partialTick, poses, buffers, light, overlay);
        poses.popPose();
    }

    @Override
    public void createVerticesOfQuad(GeoQuad quad, Matrix4f pose, Vector3f normal, VertexConsumer buffer,
                                     int light, int overlay, int color) {
        float minX = Float.POSITIVE_INFINITY, minY = Float.POSITIVE_INFINITY;
        float maxX = Float.NEGATIVE_INFINITY, maxY = Float.NEGATIVE_INFINITY;
        for (var vertex : quad.vertices()) {
            pose.transformPosition(vertex.position(), transformed);
            toAperture.transformPosition(transformed, aperture);
            minX = Math.min(minX, aperture.x); maxX = Math.max(maxX, aperture.x);
            minY = Math.min(minY, aperture.y); maxY = Math.max(maxY, aperture.y);
        }
        if (maxX <= -1.5F || minX >= 1.5F || maxY <= -1.5F || minY >= 1.5F) {
            super.createVerticesOfQuad(quad, pose, normal, buffer, light, overlay, color);
            return;
        }
        if (minX >= -1.5F && maxX <= 1.5F && minY >= -1.5F && maxY <= 1.5F) return;
        var face = new ArrayList<RiftApertureClip.Vertex>(4);
        for (var vertex : quad.vertices()) {
            pose.transformPosition(vertex.position(), transformed);
            toAperture.transformPosition(transformed, aperture);
            face.add(new RiftApertureClip.Vertex(transformed.x, transformed.y, transformed.z,
                    aperture.x, aperture.y, vertex.texU(), vertex.texV()));
        }
        for (var polygon : RiftApertureClip.outside(face)) {
            for (int i = 1; i < polygon.size() - 1; i++) {
                emit(buffer, polygon.getFirst(), normal, light, overlay, color);
                emit(buffer, polygon.get(i), normal, light, overlay, color);
                emit(buffer, polygon.get(i + 1), normal, light, overlay, color);
                emit(buffer, polygon.get(i + 1), normal, light, overlay, color);
            }
        }
    }

    private static void emit(VertexConsumer buffer, RiftApertureClip.Vertex v, Vector3f normal, int light, int overlay, int color) {
        buffer.addVertex(v.x(), v.y(), v.z(), color, v.u(), v.v(), overlay, light, normal.x, normal.y, normal.z);
    }
}
