package com.thunder.wildernessodysseyapi.weather.client.cloud;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.thunder.wildernessodysseyapi.core.ModConstants;
import com.thunder.wildernessodysseyapi.watersystem.water.render.WaterShaders;
import com.thunder.wildernessodysseyapi.weather.config.WeatherRenderingConfig;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.client.event.RegisterShadersEvent;
import org.lwjgl.opengl.GL20;

import java.io.IOException;

/**
 * Owns the optional procedural shader used by layered 3D cloud columns.
 *
 * <p>Resource reload or shader-link failure is deliberately non-fatal. Fancy
 * clouds then fall back to the existing voxel mesh, while Fast clouds retain
 * the inexpensive flat path. External Iris/Oculus packs remain authoritative.</p>
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
            ModConstants.LOGGER.error("Volumetric cloud shader failed to link; using voxel cloud fallback");
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
                && !WaterShaders.externalShaderPackOwnsWater();
    }

    /** Updates per-frame motion, lighting, and world-origin uniforms. */
    public static void updateUniforms(
            float timeSeconds,
            double windOffsetX,
            double windOffsetZ,
            int originTileX,
            int originTileZ,
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
    }

    /** Scale shared by mesh UV coordinates and the world-origin uniform. */
    public static float worldNoiseScale() {
        return WORLD_NOISE_SCALE;
    }

    private static boolean isLinked(ShaderInstance shader) {
        int programId = shader.getId();
        return GL20.glIsProgram(programId)
                && GL20.glGetProgrami(programId, GL20.GL_LINK_STATUS) != 0;
    }
}
