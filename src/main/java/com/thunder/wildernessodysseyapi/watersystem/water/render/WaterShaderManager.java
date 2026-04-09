package com.thunder.wildernessodysseyapi.watersystem.water.render;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.client.event.RegisterShadersEvent;

import java.io.IOException;

/**
 * WaterShaderManager
 *
 * Registers a custom GLSL shader for water surface rendering.
 * The shader receives a GameTime uniform so the vertex shader
 * can animate UV scrolling and normals independently of Java-side
 * wave displacement (the two effects layer nicely).
 *
 * Shader files live at:
 *   assets/wilderness/shaders/core/water_surface.vsh
 *   assets/wilderness/shaders/core/water_surface.fsh
 *   assets/wilderness/shaders/core/water_surface.json
 */
public class WaterShaderManager {

    public static ShaderInstance waterSurfaceShader;

    public static void registerShaders(RegisterShadersEvent event) {
        try {
            event.registerShader(
                new ShaderInstance(
                    event.getResourceProvider(),
                    ResourceLocation.fromNamespaceAndPath("wilderness", "water_surface"),
                    DefaultVertexFormat.BLOCK
                ),
                shader -> waterSurfaceShader = shader
            );
        } catch (IOException e) {
            throw new RuntimeException("Failed to register water_surface shader", e);
        }
    }
}
