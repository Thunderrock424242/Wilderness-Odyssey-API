package com.thunder.wildernessodysseyapi.watersystem.water.render;

import com.thunder.wildernessodysseyapi.core.ModConstants;
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
 *   assets/wildernessodysseyapi/shaders/core/volumetric_surface_water.vsh
 *   assets/wildernessodysseyapi/shaders/core/volumetric_surface_water.fsh
 *   assets/wildernessodysseyapi/shaders/core/volumetric_surface_water.json
 */
public class WaterShaderManager {

    public static ShaderInstance waterSurfaceShader;

    public static void registerShaders(RegisterShadersEvent event) {
        try {
            event.registerShader(
                new ShaderInstance(
                    event.getResourceProvider(),
                    ResourceLocation.fromNamespaceAndPath(ModConstants.MOD_ID, "volumetric_surface_water"),
                    DefaultVertexFormat.BLOCK
                ),
                shader -> waterSurfaceShader = shader
            );
        } catch (IOException e) {
            throw new RuntimeException("Failed to register volumetric_surface_water shader", e);
        }
    }
}
