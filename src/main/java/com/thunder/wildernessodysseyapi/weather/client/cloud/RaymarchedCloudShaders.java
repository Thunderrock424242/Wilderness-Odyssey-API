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
 * Owns the optional shader that integrates procedural density through a cloud slab.
 *
 * <p>The shader consumes the same synchronized cloud field as every fallback.
 * It does not simulate weather, and external shader packs remain authoritative.</p>
 */
public final class RaymarchedCloudShaders {

    private static ShaderInstance cloudShader;

    private RaymarchedCloudShaders() {
    }

    /** Registers the raymarch program during NeoForge's client shader event. */
    public static void register(RegisterShadersEvent event) throws IOException {
        event.registerShader(
                new ShaderInstance(
                        event.getResourceProvider(),
                        ResourceLocation.fromNamespaceAndPath(ModConstants.MOD_ID, "raymarched_clouds"),
                        DefaultVertexFormat.POSITION_TEX_COLOR_NORMAL
                ),
                RaymarchedCloudShaders::acceptShader
        );
    }

    private static void acceptShader(ShaderInstance shader) {
        if (!isLinked(shader)) {
            ModConstants.LOGGER.error("Raymarched cloud shader failed to link; using layered cloud fallback");
            cloudShader = null;
            return;
        }
        cloudShader = shader;
    }

    /** Returns the raymarch program, with a stock value only for defensive setup. */
    public static ShaderInstance getShader() {
        return cloudShader != null ? cloudShader : GameRenderer.getRendertypeCloudsShader();
    }

    /** Returns whether this tier may own Fancy cloud geometry. */
    public static boolean shouldUse(WeatherRenderingConfig.Settings settings) {
        return settings != null
                && settings.raymarchedClouds()
                && cloudShader != null
                && !WaterShaders.externalShaderPackOwnsWater();
    }

    /** Updates camera-relative ray entry, motion, lighting, and sample-count uniforms. */
    public static void updateUniforms(
            float timeSeconds,
            double windOffsetX,
            double windOffsetZ,
            int originTileX,
            int originTileZ,
            double cameraX,
            double cameraY,
            double cameraZ,
            float cloudHeight,
            float sunAngle,
            WeatherRenderingConfig.Settings settings
    ) {
        if (cloudShader == null) {
            return;
        }
        float tileSize = CloudCoverageModel.CLOUD_TILE_SIZE;
        float noiseScale = VolumetricCloudShaders.worldNoiseScale();
        cloudShader.safeGetUniform("GameTime").set(timeSeconds);
        cloudShader.safeGetUniform("WorldOrigin").set(
                originTileX * tileSize * noiseScale,
                originTileZ * tileSize * noiseScale
        );
        cloudShader.safeGetUniform("WindOffset").set(
                (float) windOffsetX * noiseScale,
                (float) windOffsetZ * noiseScale
        );
        cloudShader.safeGetUniform("CameraPosition").set(
                (float) (cameraX - originTileX * tileSize),
                (float) (cameraY - cloudHeight - CLOUD_BASE_OFFSET),
                (float) (cameraZ - originTileZ * tileSize)
        );
        cloudShader.safeGetUniform("DetailStrength").set((float) settings.volumetricDetailStrength());
        cloudShader.safeGetUniform("RaymarchSteps").set(settings.raymarchSteps());
        cloudShader.safeGetUniform("SunDirection").set(
                (float) Math.cos(sunAngle),
                (float) Math.sin(sunAngle),
                0.25F
        );
    }

    private static boolean isLinked(ShaderInstance shader) {
        int programId = shader.getId();
        return GL20.glIsProgram(programId)
                && GL20.glGetProgrami(programId, GL20.GL_LINK_STATUS) != 0;
    }

    private static final double CLOUD_BASE_OFFSET = 0.33;
}
