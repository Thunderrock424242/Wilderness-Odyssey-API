package com.thunder.wildernessodysseyapi.NovaAPI.RenderEngine.model;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.entity.EntityRenderers;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class ModdedModelLoader {
    private static final Map<EntityType<?>, ResourceLocation> entityModelMap = new ConcurrentHashMap<>();

    public static void initialize() {
        Minecraft mc = Minecraft.getInstance();
        for (EntityType<?> entityType : EntityType.values()) {
            if (EntityRenderers.getRenderer(entityType) != null) {
                ResourceLocation modelPath = getEntityModelPath(entityType);
                entityModelMap.put(entityType, modelPath);
                ModelStreamingManager.requestModel(modelPath);
            }
        }
    }

    private static ResourceLocation getEntityModelPath(EntityType<?> entityType) {
        return new ResourceLocation(entityType.getRegistryName().getNamespace(), "models/entity/" + entityType.getRegistryName().getPath() + ".json");
    }

    public static ResourceLocation getModelForEntity(EntityType<?> entityType) {
        return entityModelMap.getOrDefault(entityType, new ResourceLocation("minecraft", "models/entity/default.json"));
    }
}
