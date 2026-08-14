package com.thunder.wildernessodysseyapi.developmentstudio.debug.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.thunder.wildernessodysseyapi.core.ModConstants;
import com.thunder.wildernessodysseyapi.developmentstudio.client.StudioClientState;
import com.thunder.wildernessodysseyapi.developmentstudio.network.OpenStudioPayload;
import com.thunder.wildernessodysseyapi.developmentstudio.region.StudioTestRegion;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;

/** Draws persisted test-region bounds without collecting data when disabled. */
public final class StudioRegionBoundsRenderer implements StudioDebugRenderer {
    public static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath(
            ModConstants.MOD_ID, "test_region_bounds"
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
        if (snapshot == null || minecraft.level == null || snapshot.testRegions().isEmpty()) {
            return;
        }
        ResourceLocation dimension = minecraft.level.dimension().location();
        var camera = event.getCamera().getPosition();
        var buffers = minecraft.renderBuffers().bufferSource();
        var renderType = RenderType.lines();
        var vertices = buffers.getBuffer(renderType);
        PoseStack poses = event.getPoseStack();
        poses.pushPose();
        poses.translate(-camera.x, -camera.y, -camera.z);
        for (StudioTestRegion region : snapshot.testRegions()) {
            if (!region.dimension().equals(dimension)) {
                continue;
            }
            float[] color = color(region);
            LevelRenderer.renderLineBox(poses, vertices, region.bounds(),
                    color[0], color[1], color[2], 0.9F);
        }
        poses.popPose();
        buffers.endBatch(renderType);
    }

    private static float[] color(StudioTestRegion region) {
        return switch (region.type()) {
            case STRUCTURE -> new float[]{0.45F, 0.75F, 1.0F};
            case ENTITY -> new float[]{1.0F, 0.35F, 0.35F};
            case WATER -> new float[]{0.2F, 0.9F, 0.95F};
            case OUTDOOR -> new float[]{0.35F, 1.0F, 0.45F};
        };
    }
}
