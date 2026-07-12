package com.thunder.wildernessodysseyapi.watersystem.water.render;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.systems.RenderSystem;
import com.thunder.wildernessodysseyapi.core.ModConstants;
import com.thunder.wildernessodysseyapi.watersystem.ocean.tide.TideSystem;
import com.thunder.wildernessodysseyapi.watersystem.water.wave.GerstnerWaveProfile;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.client.event.RegisterShadersEvent;
import org.lwjgl.opengl.GL20;
import org.joml.Matrix4f;

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
    private static boolean sceneCaptureFailureLogged;

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
    // the per-frame ocean pass can keep drawing the Wilderness mesh with the
    // stock translucent program instead of retrying an invalid shader.
    private static void acceptOceanShader(ShaderInstance shader) {
        if (!isLinked(shader)) {
            ModConstants.LOGGER.error("Ocean shader failed to link; drawing the Wilderness ocean mesh with the stock translucent shader");
            oceanShader = null;
            return;
        }
        oceanShader = shader;
        sceneCaptureFailureLogged = false;
    }

    // Underwater optics are optional for the same reason as the surface shader:
    // a bad resource reload must not prevent the client from reaching the menu.
    private static void acceptUnderwaterShader(ShaderInstance shader) {
        if (!isLinked(shader)) {
            ModConstants.LOGGER.error("Underwater shader failed to link; drawing the Wilderness underwater overlay with the stock texture shader");
            underwaterShader = null;
            return;
        }
        underwaterShader = shader;
    }

    /** Returns the program used by the custom Wilderness ocean RenderType. */
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

    /** Returns the program used by the Wilderness underwater overlay. */
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
        if (oceanShader == null) {
            return;
        }

        oceanShader.safeGetUniform("GameTime").set(timeSeconds);
        oceanShader.safeGetUniform("SeaState").set(seaState);
        oceanShader.safeGetUniform("WindDirection").set(windDirectionX, windDirectionZ);
        oceanShader.safeGetUniform("DayTime").set(dayTime);

        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null) {
            oceanShader.safeGetUniform("SceneCaptureValid").set(0.0f);
            return;
        }

        float partialTick = minecraft.gameRenderer.getMainCamera().getPartialTickTime();
        long frameKey = (minecraft.level.getGameTime() << 32)
                ^ (Float.floatToRawIntBits(timeSeconds) & 0xFFFFFFFFL);
        WaterSceneCapture.Capture capture = null;
        try {
            capture = WaterSceneCapture.capture(frameKey);
        } catch (RuntimeException exception) {
            if (!sceneCaptureFailureLogged) {
                ModConstants.LOGGER.warn("Unable to capture scene color/depth for optical water; using environment reflection", exception);
                sceneCaptureFailureLogged = true;
            }
        }

        if (capture != null && capture.available()) {
            oceanShader.setSampler("SceneColor", capture.colorTextureId());
            oceanShader.setSampler("SceneDepth", capture.depthTextureId());
            oceanShader.safeGetUniform("ScreenSize").set((float) capture.width(), (float) capture.height());
            oceanShader.safeGetUniform("SceneCaptureValid").set(1.0f);
        } else {
            oceanShader.safeGetUniform("SceneCaptureValid").set(0.0f);
        }

        Matrix4f inverseProjection = new Matrix4f(RenderSystem.getProjectionMatrix()).invert();
        oceanShader.safeGetUniform("InverseProjMat").set(inverseProjection);
        var cameraPosition = minecraft.gameRenderer.getMainCamera().getPosition();

        float rain = minecraft.level.getRainLevel(partialTick);
        float thunder = minecraft.level.getThunderLevel(partialTick);
        float skyBrightness = Math.max(0.12f, 1.0f - rain * 0.32f - thunder * 0.38f);
        oceanShader.safeGetUniform("Weather").set(rain, thunder, skyBrightness, 0.0f);
        var sky = minecraft.level.getSkyColor(cameraPosition, partialTick);
        oceanShader.safeGetUniform("EnvironmentColor").set(
                (float) sky.x,
                (float) sky.y,
                (float) sky.z,
                skyBrightness
        );

        oceanShader.safeGetUniform("OpticalQuality").set(
                (float) WaterRenderingConfig.waterQuality().ordinal(),
                WaterRenderingConfig.refractionStrength(),
                (float) WaterRenderingConfig.screenSpaceReflectionSteps(),
                WaterRenderingConfig.screenSpaceReflectionDistance()
        );
        float absorption = WaterRenderingConfig.surfaceAbsorptionStrength();
        oceanShader.safeGetUniform("AbsorptionCoefficients").set(
                0.145f * absorption,
                0.052f * absorption,
                0.024f * absorption
        );
        oceanShader.safeGetUniform("TideOffset").set(
                TideSystem.getTideOffset(minecraft.level) * 0.18f
        );
        oceanShader.safeGetUniform("GpuWaveStrength").set(
                WaterSurfaceEquation.LEGACY_GPU_COMPLEMENT_SCALE
        );
        updateWaveUniforms("Ocean", GerstnerWaveProfile.OCEAN);
        updateWaveUniforms("River", GerstnerWaveProfile.RIVER);
        updateWaveUniforms("Pond", GerstnerWaveProfile.POND);
    }

    // Existing meshes already contain their broad CPU Gerstner shape. These
    // uniforms add a small continuous GPU complement now and can be raised to
    // full strength when the stable snapshot mesh becomes the only geometry.
    private static void updateWaveUniforms(String prefix, GerstnerWaveProfile profile) {
        for (int index = 0; index < 4; index++) {
            boolean active = index < profile.waveCount;
            int profileIndex = active ? index : 0;
            float componentBlend = profile.waveCount <= 1
                    ? 0.0f
                    : profileIndex / (float) (profile.waveCount - 1);
            oceanShader.safeGetUniform(prefix + "WaveParam" + index).set(
                    profile.dirX[profileIndex],
                    profile.dirZ[profileIndex],
                    profile.waveNumber[profileIndex],
                    profile.angularFrequency[profileIndex]
            );
            oceanShader.safeGetUniform(prefix + "WaveShape" + index).set(
                    active ? profile.amplitude[profileIndex] : 0.0f,
                    profile.phaseOffset[profileIndex],
                    profile.steepness[profileIndex],
                    componentBlend
            );
        }
    }

    /** Enables full GPU displacement for stable snapshot meshes with flat CPU topology. */
    public static void prepareSnapshotMeshPass() {
        if (oceanShader != null) {
            oceanShader.safeGetUniform("GpuWaveStrength").set(1.0f);
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
