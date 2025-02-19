package com.thunder.wildernessodysseyapi.RenderEngine.texture;

import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.resources.ResourceLocation;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class TextureStreamingManager {
    private static final Map<ResourceLocation, Integer> loadedTextures = new ConcurrentHashMap<>();
    private static final TextureManager textureManager = new TextureManager(null);

    public static void loadTexture(ResourceLocation texturePath) {
        if (!loadedTextures.containsKey(texturePath)) {
            int textureID = textureManager.register(texturePath);
            loadedTextures.put(texturePath, textureID);
        }
    }

    public static void unloadTexture(ResourceLocation texturePath) {
        if (loadedTextures.containsKey(texturePath)) {
            textureManager.release(texturePath);
            loadedTextures.remove(texturePath);
        }
    }
}
