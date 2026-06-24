package com.thunder.wildernessodysseyapi.watersystem.water.render;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.thunder.wildernessodysseyapi.core.ModConstants;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.client.event.RegisterShadersEvent;
import org.lwjgl.opengl.GL20;

import java.io.IOException;

/**
 * Owns the optional built-in ocean shader and its frame uniforms.
 *
 * <p>Iris/Oculus installations stay on the normal translucent RenderType so
 * the active shader pack remains the final authority over water shading.</p>
 */
public final class WaterShaders {

    private static ShaderInstance oceanShader;
    private static ShaderInstance underwaterShader;

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
                WaterShaders::acceptOceanShader
        );
        event.registerShader(
                new ShaderInstance(
                        event.getResourceProvider(),
                        ResourceLocation.fromNamespaceAndPath(ModConstants.MOD_ID, "underwater_optics"),
                        DefaultVertexFormat.POSITION_TEX
                ),
                WaterShaders::acceptUnderwaterShader
        );
    }

    // A failed GLSL link can still produce a ShaderInstance. Reject it here so
    // the per-frame ocean pass falls back instead of retrying an invalid program.
    private static void acceptOceanShader(ShaderInstance shader) {
        if (!isLinked(shader)) {
            ModConstants.LOGGER.error("Ocean shader failed to link; using the vanilla translucent fallback");
            oceanShader = null;
            return;
        }
        oceanShader = shader;
    }

    // Underwater optics are optional for the same reason as the surface shader:
    // a bad resource reload must not prevent the client from reaching the menu.
    private static void acceptUnderwaterShader(ShaderInstance shader) {
        if (!isLinked(shader)) {
            ModConstants.LOGGER.error("Underwater shader failed to link; using the vanilla water overlay");
            underwaterShader = null;
            return;
        }
        underwaterShader = shader;
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
        return !isExternalShaderPackModLoaded();
    }

    /** Returns whether the built-in underwater overlay should own this frame. */
    public static boolean shouldUseUnderwaterShader() {
        return WaterRenderingConfig.ENABLE_UNDERWATER_CAUSTICS.get()
                && underwaterShader != null
                && !isExternalShaderPackModLoaded();
    }

    /** Returns the linked underwater shader, falling back to vanilla position-texture rendering. */
    public static ShaderInstance getUnderwaterShader() {
        return underwaterShader != null ? underwaterShader : GameRenderer.getPositionTexShader();
    }

    /** Updates uniforms consumed by the built-in optical water pass. */
    public static void updateOceanUniforms(
            float timeSeconds,
            float seaState,
            float windDirectionX,
            float windDirectionZ,
            float dayTime
    ) {
        if (oceanShader != null) {
            oceanShader.safeGetUniform("GameTime").set(timeSeconds);
            oceanShader.safeGetUniform("SeaState").set(seaState);
            oceanShader.safeGetUniform("WindDirection").set(windDirectionX, windDirectionZ);
            oceanShader.safeGetUniform("DayTime").set(dayTime);
        }
    }

    /** Updates the bounded uniforms consumed by the underwater overlay. */
    public static void updateUnderwaterUniforms(
            float timeSeconds,
            ClientWaterImmersion.ImmersionState state
    ) {
        if (underwaterShader == null) {
            return;
        }
        UnderwaterOpticsModel.OpticalProperties optics = state.optics();
        underwaterShader.safeGetUniform("GameTime").set(timeSeconds);
        underwaterShader.safeGetUniform("Submersion").set(optics.immersionBlend());
        underwaterShader.safeGetUniform("Clarity").set(optics.clarity());
        underwaterShader.safeGetUniform("SeaState").set(state.seaState());
        underwaterShader.safeGetUniform("CausticStrength").set(optics.causticStrength());
        underwaterShader.safeGetUniform("DistortionStrength").set(optics.distortionStrength());
        underwaterShader.safeGetUniform("WaterFogColor").set(
                optics.fogRed(),
                optics.fogGreen(),
                optics.fogBlue()
        );
    }

    private static boolean isExternalShaderPackModLoaded() {
        ModList mods = ModList.get();
        return mods.isLoaded("iris") || mods.isLoaded("oculus");
    }

    private static boolean isLinked(ShaderInstance shader) {
        int programId = shader.getId();
        return GL20.glIsProgram(programId)
                && GL20.glGetProgrami(programId, GL20.GL_LINK_STATUS) != 0;
    }
}
