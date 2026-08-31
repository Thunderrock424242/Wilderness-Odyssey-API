package com.thunder.wildernessodysseyapi.weather.client.cloud;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.thunder.wildernessodysseyapi.core.ModConstants;
import com.thunder.wildernessodysseyapi.rendering.RenderingQuality;
import com.thunder.wildernessodysseyapi.rendering.backend.RenderBackends;
import com.thunder.wildernessodysseyapi.rendering.client.WildernessRenderingFramework;
import com.thunder.wildernessodysseyapi.rendering.compat.ShaderPackCompatibility;
import com.thunder.wildernessodysseyapi.rendering.performance.RenderQualityState;
import com.thunder.wildernessodysseyapi.weather.config.WeatherRenderingConfig;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.client.event.RegisterShadersEvent;

import java.io.IOException;

/**
 * Owns the optional procedural shader used by layered 3D cloud columns.
 *
 * <p>Resource reload or shader-link failure is deliberately non-fatal. Fancy
 * clouds then fall back to density-derived lobe shells, while Fast clouds retain
 * the cheaper lobe path. External Iris/Oculus packs remain authoritative.</p>
 */
public final class VolumetricCloudShaders {

    private static final float WORLD_NOISE_SCALE = 1.0F / 64.0F;
    private static ShaderInstance cloudShader;

    private VolumetricCloudShaders() {
    }

    /** Registers the cloud shader during NeoForge's client shader event. */
    public static void register(RegisterShadersEvent event) throws IOException {
        event.registerShader(
                new ShaderInstance(
                        event.getResourceProvider(),
                        ResourceLocation.fromNamespaceAndPath(ModConstants.MOD_ID, "volumetric_clouds"),
                        DefaultVertexFormat.POSITION_TEX_COLOR_NORMAL
                ),
                VolumetricCloudShaders::acceptShader
        );
    }

    private static void acceptShader(ShaderInstance shader) {
        if (!isLinked(shader)) {
            ModConstants.LOGGER.error("Volumetric cloud shader failed to link; using cloud-lobe fallback");
            cloudShader = null;
            return;
        }
        cloudShader = shader;
    }

    /** Returns the program used by the cloud render type, with a safe stock fallback. */
    public static ShaderInstance getShader() {
        return cloudShader != null ? cloudShader : GameRenderer.getRendertypeCloudsShader();
    }

    /** Returns whether the layered volume path should own Fancy clouds. */
    public static boolean shouldUse(WeatherRenderingConfig.Settings settings) {
        return settings != null
                && settings.volumetricClouds()
                && cloudShader != null
                && RenderQualityState.currentQuality().allows(RenderingQuality.MEDIUM)
                && WildernessRenderingFramework.currentFrame().gpuCapabilities().available()
                && !ShaderPackCompatibility.isExternalShaderPackActive();
    }

    /** Updates per-frame motion, lighting, and world-origin uniforms. */
    public static void updateUniforms(
            ClientLevel level,
            float timeSeconds,
            double windOffsetX,
            double windOffsetZ,
            int originTileX,
            int originTileZ,
            float cloudHeight,
            float sunAngle,
            double detailStrength
    ) {
        if (cloudShader == null) {
            return;
        }
        float worldOriginX = originTileX * CloudCoverageModel.CLOUD_TILE_SIZE * WORLD_NOISE_SCALE;
        float worldOriginZ = originTileZ * CloudCoverageModel.CLOUD_TILE_SIZE * WORLD_NOISE_SCALE;
        cloudShader.safeGetUniform("GameTime").set(timeSeconds);
        cloudShader.safeGetUniform("WorldOrigin").set(worldOriginX, worldOriginZ);
        cloudShader.safeGetUniform("WindOffset").set(
                (float) windOffsetX * WORLD_NOISE_SCALE,
                (float) windOffsetZ * WORLD_NOISE_SCALE
        );
        cloudShader.safeGetUniform("DetailStrength").set((float) detailStrength);
        cloudShader.safeGetUniform("SunDirection").set(
                (float) Math.cos(sunAngle),
                (float) Math.sin(sunAngle),
                0.25F
        );
        WeatherLightningIllumination.State lightning = WeatherLightningIllumination.current(level);
        cloudShader.safeGetUniform("LightningPosition").set(
                (float) (lightning.position().x
                        - originTileX * (double) CloudCoverageModel.CLOUD_TILE_SIZE),
                (float) (lightning.position().y - CloudAltitudeModel.dimensionBaseY(cloudHeight)),
                (float) (lightning.position().z
                        - originTileZ * (double) CloudCoverageModel.CLOUD_TILE_SIZE)
        );
        cloudShader.safeGetUniform("LightningIllumination").set(lightning.illumination());
    }

    /** Scale shared by mesh UV coordinates and the world-origin uniform. */
    public static float worldNoiseScale() {
        return WORLD_NOISE_SCALE;
    }

    private static boolean isLinked(ShaderInstance shader) {
        return RenderBackends.current().isShaderUsable(shader);
    }
}
