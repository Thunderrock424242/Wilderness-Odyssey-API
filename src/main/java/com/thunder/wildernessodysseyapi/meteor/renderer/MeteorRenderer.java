package com.thunder.wildernessodysseyapi.meteor.renderer;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import com.thunder.wildernessodysseyapi.meteor.entity.MeteorEntity;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Renders the meteor as a jagged cluster of block faces:
 *   - Central magma block (glowing)
 *   - Surrounding crying obsidian fragments offset randomly
 *   - Rotates over time so it looks like it's tumbling
 *
 * No custom model needed — we reuse vanilla block rendering.
 */
public class MeteorRenderer extends EntityRenderer<MeteorEntity> {

    private final BlockRenderDispatcher blockRenderer;

    // Pre-baked offsets for the "chunk" blocks that surround the core
    // (x, y, z, scale) — each entry is one sub-block in the cluster
    private static final float[][] CHUNK_OFFSETS = {
            {  0.00f,  0.00f,  0.00f, 1.00f },  // core magma — full size
            {  0.55f,  0.20f,  0.10f, 0.65f },  // crying obsidian fragment
            { -0.50f,  0.15f,  0.20f, 0.60f },
            {  0.10f,  0.55f, -0.30f, 0.55f },
            {  0.20f, -0.50f,  0.40f, 0.58f },
            { -0.30f, -0.40f, -0.50f, 0.52f },
            {  0.40f, -0.20f, -0.55f, 0.50f },
            { -0.10f,  0.45f,  0.50f, 0.48f },
    };

    // Which block each chunk uses (index 0 = magma, rest = crying obsidian)
    private static final BlockState[] CHUNK_BLOCKS;

    static {
        CHUNK_BLOCKS = new BlockState[CHUNK_OFFSETS.length];
        CHUNK_BLOCKS[0] = Blocks.MAGMA_BLOCK.defaultBlockState();
        for (int i = 1; i < CHUNK_BLOCKS.length; i++) {
            CHUNK_BLOCKS[i] = Blocks.CRYING_OBSIDIAN.defaultBlockState();
        }
    }

    public MeteorRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.blockRenderer = context.getBlockRenderDispatcher();
        this.shadowRadius = 0.8f;
    }

    @Override
    public void render(MeteorEntity entity, float entityYaw, float partialTick,
                       PoseStack poseStack, MultiBufferSource buffers, int packedLight) {

        poseStack.pushPose();

        // Tumble rotation based on entity age + partial tick
        float age = entity.tickCount + partialTick;
        float tumbleX = age * 2.3f;
        float tumbleY = age * 1.7f;
        float tumbleZ = age * 1.1f;

        poseStack.mulPose(Axis.YP.rotationDegrees(tumbleY));
        poseStack.mulPose(Axis.XP.rotationDegrees(tumbleX));
        poseStack.mulPose(Axis.ZP.rotationDegrees(tumbleZ));

        // Center the block cluster on the entity origin
        poseStack.translate(-0.5, -0.5, -0.5);

        for (int i = 0; i < CHUNK_OFFSETS.length; i++) {
            float[] c = CHUNK_OFFSETS[i];
            float ox = c[0], oy = c[1], oz = c[2], scale = c[3];

            poseStack.pushPose();
            poseStack.translate(ox, oy, oz);
            poseStack.scale(scale, scale, scale);

            // Magma block is emissive — use full bright light for it
            int light = (i == 0) ? (15 << 4 | 15 << 20) : packedLight;

            VertexConsumer consumer = buffers.getBuffer(RenderType.solid());
            blockRenderer.renderSingleBlock(
                    CHUNK_BLOCKS[i],
                    poseStack,
                    buffers,
                    light,
                    OverlayTexture.NO_OVERLAY
            );

            poseStack.popPose();
        }

        poseStack.popPose();

        super.render(entity, entityYaw, partialTick, poseStack, buffers, packedLight);
    }

    @Override
    public ResourceLocation getTextureLocation(MeteorEntity entity) {
        // No single texture — we use block rendering above
        return ResourceLocation.withDefaultNamespace("textures/block/magma.png");
    }
}