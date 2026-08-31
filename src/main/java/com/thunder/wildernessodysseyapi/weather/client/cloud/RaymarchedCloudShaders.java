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
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.RegisterShadersEvent;

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
                && RenderQualityState.currentQuality().allows(RenderingQuality.HIGH)
                && WildernessRenderingFramework.currentFrame().gpuCapabilities().supportsHighQualityVolumetrics()
                && !ShaderPackCompatibility.isExternalShaderPackActive();
    }

    /** Updates continuous-field mappings, camera-relative rays, motion, and lighting uniforms. */
    public static void updateUniforms(
            ClientLevel level,
            float timeSeconds,
            double windOffsetX,
            double windOffsetZ,
            int renderCenterX,
            int renderCenterZ,
            double cameraX,
            double cameraY,
            double cameraZ,
            float cloudHeight,
            float sunAngle,
            Vec3 baseColor,
            WeatherRenderingConfig.Settings settings,
            ContinuousCloudFieldAtlas.State atlas
    ) {
        if (cloudShader == null || atlas == null || !atlas.active()) {
            return;
        }
        CloudFieldAtlasModel.Layout previous = atlas.previousLayout();
        CloudFieldAtlasModel.Layout current = atlas.currentLayout();
        ContinuousCloudFieldAtlas.bindSamplers(cloudShader);
        cloudShader.safeGetUniform("GameTime").set(timeSeconds);
        cloudShader.safeGetUniform("RenderOrigin").set((float) renderCenterX, (float) renderCenterZ);
        cloudShader.safeGetUniform("WindOffset").set(
                (float) windOffsetX,
                (float) windOffsetZ
        );
        cloudShader.safeGetUniform("CameraPosition").set(
                (float) (cameraX - renderCenterX),
                (float) (cameraY - cloudHeight - CLOUD_BASE_OFFSET),
                (float) (cameraZ - renderCenterZ)
        );
        cloudShader.safeGetUniform("PreviousNearField").set(
                (float) previous.nearOriginBlockX(),
                (float) previous.nearOriginBlockZ(),
                (float) previous.nearSpacingBlocks(),
                (float) previous.nearDimension()
        );
        cloudShader.safeGetUniform("CurrentNearField").set(
                (float) current.nearOriginBlockX(),
                (float) current.nearOriginBlockZ(),
                (float) current.nearSpacingBlocks(),
                (float) current.nearDimension()
        );
        cloudShader.safeGetUniform("PreviousDistantField").set(
                (float) previous.distantOriginBlockX(),
                (float) previous.distantOriginBlockZ(),
                (float) previous.distantSpacingBlocks(),
                (float) previous.distantDimension()
        );
        cloudShader.safeGetUniform("CurrentDistantField").set(
                (float) current.distantOriginBlockX(),
                (float) current.distantOriginBlockZ(),
                (float) current.distantSpacingBlocks(),
                (float) current.distantDimension()
        );
        cloudShader.safeGetUniform("FieldTextureSize").set(
                (float) current.atlasWidth(),
                (float) current.atlasHeight()
        );
        cloudShader.safeGetUniform("FieldBlend").set(atlas.blend());
        cloudShader.safeGetUniform("NearRadius").set((float) current.nearRadiusBlocks());
        cloudShader.safeGetUniform("DistantRadius").set((float) current.distantRadiusBlocks());
        cloudShader.safeGetUniform("DetailStrength").set((float) settings.volumetricDetailStrength());
        cloudShader.safeGetUniform("RaymarchSteps").set(settings.raymarchSteps());
        cloudShader.safeGetUniform("LightingSteps").set(current.quality().lightingSteps());
        Vec3 color = baseColor == null ? new Vec3(1.0, 1.0, 1.0) : baseColor;
        cloudShader.safeGetUniform("CloudColor").set(
                (float) color.x,
                (float) color.y,
                (float) color.z
        );
        cloudShader.safeGetUniform("SunDirection").set(
                (float) Math.cos(sunAngle),
                (float) Math.sin(sunAngle),
                0.25F
        );
        WeatherLightningIllumination.State lightning = WeatherLightningIllumination.current(level);
        cloudShader.safeGetUniform("LightningPosition").set(
                (float) (lightning.position().x - renderCenterX),
                (float) (lightning.position().y - cloudHeight - CLOUD_BASE_OFFSET),
                (float) (lightning.position().z - renderCenterZ)
        );
        cloudShader.safeGetUniform("LightningIllumination").set(lightning.illumination());
    }

    private static boolean isLinked(ShaderInstance shader) {
        return RenderBackends.current().isShaderUsable(shader);
    }

    private static final double CLOUD_BASE_OFFSET = 0.33;
}
