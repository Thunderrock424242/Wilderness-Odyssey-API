package com.thunder.wildernessodysseyapi.NovaAPI.RenderEngine.model;

import com.thunder.wildernessodysseyapi.NovaAPI.RenderEngine.Threading.RenderThreadManager;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.client.model.data.ModelData;

import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

public class ModelStreamingManager {
    private static final Map<ResourceLocation, ModelData> loadedModels = new ConcurrentHashMap<>();

    public static CompletableFuture<ModelData> requestModel(ResourceLocation modelPath) {
        return CompletableFuture.supplyAsync(() -> {
            if (loadedModels.containsKey(modelPath)) {
                return loadedModels.get(modelPath);
            }

            ModelData model = loadModel(modelPath);
            loadedModels.put(modelPath, model);
            return model;
        }, RenderThreadManager::execute);
    }

    public static void unloadUnusedModels() {
        loadedModels.entrySet().removeIf(entry -> !isModelInUse(entry.getKey()));
    }

    private static boolean isModelInUse(ResourceLocation modelPath) {
        return Minecraft.getInstance().level.getEntities().stream()
                .anyMatch(entity -> ModdedModelLoader.getModelForEntity(entity.getType()).equals(modelPath));
    }



    private static ModelData loadModel(ResourceLocation modelPath) {
        // TODO: Implement actual model loading (OBJ, JSON, etc.)
        return new ModelData(modelPath);
    }
}
