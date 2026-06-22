package com.thunder.wildernessodysseyapi.watersystem.water.render;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.thunder.wildernessodysseyapi.core.ModConstants;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.client.event.RegisterShadersEvent;

import java.io.IOException;

/**
 * Owns the optional built-in ocean shader and its frame uniforms.
 *
 * <p>Iris/Oculus installations stay on the normal translucent RenderType so
 * the active shader pack remains the final authority over water shading.</p>
 */
public final class WaterShaders {

    private static ShaderInstance oceanShader;

    private WaterShaders() {
    }

    /** Registers the core ocean shader during the client shader event. */
    public static void register(RegisterShadersEvent event) throws IOException {
        event.registerShader(
                new ShaderInstance(
                        event.getResourceProvider(),
                        ResourceLocation.fromNamespaceAndPath(ModConstants.MOD_ID, "gerstner_water"),
                        DefaultVertexFormat.BLOCK
                ),
                shader -> oceanShader = shader
        );
    }

    /** Returns the shader used by the custom ocean RenderType. */
    public static ShaderInstance getOceanShader() {
        return oceanShader != null ? oceanShader : GameRenderer.getRendertypeTranslucentShader();
    }

    /** Returns whether the built-in shader should own ocean pixels this frame. */
    public static boolean shouldUseCoreShader() {
        if (!WaterRenderingConfig.ENABLE_WATER_CORE_SHADER.get() || oceanShader == null) {
            return false;
        }
        ModList mods = ModList.get();
        return !mods.isLoaded("iris") && !mods.isLoaded("oculus");
    }

    /** Updates uniforms consumed by the built-in optical water pass. */
    public static void updateOceanUniforms(float timeSeconds) {
        if (oceanShader != null) {
            oceanShader.safeGetUniform("GameTime").set(timeSeconds);
        }
    }
}
