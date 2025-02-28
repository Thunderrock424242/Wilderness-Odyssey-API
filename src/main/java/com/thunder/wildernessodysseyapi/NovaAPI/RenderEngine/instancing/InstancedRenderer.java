package com.thunder.wildernessodysseyapi.NovaAPI.RenderEngine.instancing;

import com.thunder.wildernessodysseyapi.NovaAPI.RenderEngine.model.ModelStreamingManager;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.model.data.ModelData;
import org.lwjgl.opengl.GL45;

public class InstancedRenderer {
    private final ResourceLocation modelPath;
    private ModelData model;

    public InstancedRenderer(ResourceLocation modelPath) {
        this.modelPath = modelPath;
        ModelStreamingManager.requestModel(modelPath).thenAccept(loadedModel -> this.model = loadedModel);
    }
    private boolean isInFrustum(Entity entity) {
        Camera camera = Minecraft.getInstance().gameRenderer.getMainCamera();
        Vec3 cameraPos = camera.getPosition();

        return entity.getBoundingBox().intersects(cameraPos.x - 50, cameraPos.y - 50, cameraPos.z - 50,
                cameraPos.x + 50, cameraPos.y + 50, cameraPos.z + 50);
    }

    public void render(Entity entity) {
        if (!isInFrustum(entity)) return; // Skip rendering if off-screen
        if (model == null) return;

        renderModel(model);
    }

    private void renderModel(ModelData model) {
        // TODO: Implement instanced rendering for the 3D model
        GL45.glBindVertexArray(model.getVAO());
        GL45.glDrawElementsInstanced(GL45.GL_TRIANGLES, model.getIndexCount(), GL45.GL_UNSIGNED_INT, 0, 1);
    }
}
