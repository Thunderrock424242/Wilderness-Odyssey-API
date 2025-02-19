package com.thunder.wildernessodysseyapi.RenderEngine.instancing;

import com.thunder.wildernessodysseyapi.RenderEngine.model.ModelStreamingManager;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.neoforged.neoforge.client.model.data.ModelData;
import org.lwjgl.opengl.GL45;

public class InstancedRenderer {
    private final ResourceLocation modelPath;
    private ModelData model;

    public InstancedRenderer(ResourceLocation modelPath) {
        this.modelPath = modelPath;
        ModelStreamingManager.requestModel(modelPath).thenAccept(loadedModel -> this.model = loadedModel);
    }

    public void render(Entity entity) {
        if (model == null) return; // Model not loaded yet
        renderModel(model);
    }

    private void renderModel(ModelData model) {
        // TODO: Implement instanced rendering for the 3D model
        GL45.glBindVertexArray(model.getVAO());
        GL45.glDrawElementsInstanced(GL45.GL_TRIANGLES, model.getIndexCount(), GL45.GL_UNSIGNED_INT, 0, 1);
    }
}
