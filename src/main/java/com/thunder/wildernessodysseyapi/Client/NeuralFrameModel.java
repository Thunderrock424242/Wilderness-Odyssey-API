package com.thunder.wildernessodysseyapi.Client;

import com.thunder.wildernessodysseyapi.Core.ModConstants;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.resources.ResourceLocation;

public class NeuralFrameModel extends EntityModel<AbstractClientPlayer> {
    public static final ModelLayerLocation LAYER_LOCATION = new ModelLayerLocation(
            ResourceLocation.fromNamespaceAndPath(ModConstants.MOD_ID, "neural_frame"),
            "main"
    );
    private final ModelPart frame;

    public NeuralFrameModel(ModelPart root) {
        this.frame = root.getChild("frame");
    }

    public static LayerDefinition createLayer() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        root.addOrReplaceChild(
                "frame",
                CubeListBuilder.create()
                        .texOffs(0, 0)
                        .addBox(-3.0F, -5.5F, -4.0F, 6.0F, 4.0F, 0.2F, new CubeDeformation(0.0F))
                        .texOffs(0, 5)
                        .addBox(3.0F, -5.5F, -4.0F, 0.4F, 4.0F, 0.2F, new CubeDeformation(0.0F)),
                PartPose.offset(0.0F, 0.0F, 2.0F)
        );
        return LayerDefinition.create(mesh, 16, 16);
    }

    @Override
    public void setupAnim(AbstractClientPlayer entity, float limbSwing, float limbSwingAmount, float ageInTicks, float netHeadYaw, float headPitch) {
    }

    @Override
    public void renderToBuffer(PoseStack poseStack,
                               VertexConsumer vertexConsumer,
                               int packedLight,
                               int packedOverlay,
                               int packedColor) {
        frame.render(poseStack, vertexConsumer, packedLight, packedOverlay, packedColor);
    }
}
