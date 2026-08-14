package com.thunder.wildernessodysseyapi.developmentstudio.debug.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.thunder.wildernessodysseyapi.core.ModConstants;
import com.thunder.wildernessodysseyapi.developmentstudio.client.StudioClientState;
import com.thunder.wildernessodysseyapi.developmentstudio.network.OpenStudioPayload;
import com.thunder.wildernessodysseyapi.developmentstudio.structure.StudioStructurePreview;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;

/** Draws the latest server-computed structure preview box for this player. */
public final class StudioStructurePreviewRenderer implements StudioDebugRenderer {
    public static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath(
            ModConstants.MOD_ID, "structure_preview"
    );

    @Override
    public ResourceLocation id() {
        return ID;
    }

    @Override
    public RenderLevelStageEvent.Stage stage() {
        return RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS;
    }

    @Override
    public void render(Minecraft minecraft, RenderLevelStageEvent event) {
        OpenStudioPayload snapshot = StudioClientState.snapshot();
        StudioStructurePreview preview = snapshot == null ? null : snapshot.structurePreview();
        if (preview == null || minecraft.level == null
                || !preview.dimension().equals(minecraft.level.dimension().location())
                || minecraft.level.getGameTime() > preview.expiresAtGameTime()) {
            return;
        }
        var camera = event.getCamera().getPosition();
        var buffers = minecraft.renderBuffers().bufferSource();
        var renderType = RenderType.lines();
        var vertices = buffers.getBuffer(renderType);
        PoseStack poses = event.getPoseStack();
        poses.pushPose();
        poses.translate(-camera.x, -camera.y, -camera.z);
        LevelRenderer.renderLineBox(poses, vertices, preview.bounds(), 1.0F, 0.78F, 0.18F, 1.0F);
        poses.popPose();
        buffers.endBatch(renderType);
    }
}
