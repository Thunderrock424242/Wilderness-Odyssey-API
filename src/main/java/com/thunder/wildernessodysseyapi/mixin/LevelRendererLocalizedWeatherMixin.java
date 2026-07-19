package com.thunder.wildernessodysseyapi.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.blaze3d.vertex.PoseStack;
import com.thunder.wildernessodysseyapi.weather.client.ClientWeatherCoordinator;
import com.thunder.wildernessodysseyapi.weather.client.cloud.LocalizedCloudRenderer;
import com.thunder.wildernessodysseyapi.weather.client.precipitation.LocalizedPrecipitationRenderer;
import com.thunder.wildernessodysseyapi.weather.config.WeatherRenderingConfig;
import net.minecraft.client.Camera;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.DimensionSpecialEffects;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.LightTexture;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Routes vanilla cloud and precipitation fallbacks through localized weather.
 *
 * <p>The wrappers preserve dimension-owned renderers first, then replace only
 * vanilla's fallback. Global {@link ClientLevel} weather getters remain
 * untouched for compatibility with unrelated gameplay and mods.</p>
 */
@Mixin(LevelRenderer.class)
public abstract class LevelRendererLocalizedWeatherMixin {

    /**
     * Preserves custom dimension clouds, then replaces only vanilla's fallback.
     *
     * <p>There is no NeoForge event that can both suppress the fallback cloud
     * sheet and retain its Fabulous render target. Wrapping the dimension hook
     * keeps that decision at vanilla's intended cloud-ownership boundary.</p>
     */
    @WrapOperation(
            method = "renderClouds(Lcom/mojang/blaze3d/vertex/PoseStack;Lorg/joml/Matrix4f;Lorg/joml/Matrix4f;FDDD)V",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/renderer/DimensionSpecialEffects;renderClouds(Lnet/minecraft/client/multiplayer/ClientLevel;IFLcom/mojang/blaze3d/vertex/PoseStack;DDDLorg/joml/Matrix4f;Lorg/joml/Matrix4f;)Z"
            ),
            require = 1
    )
    private boolean wildernessodysseyapi$renderLocalizedClouds(
            DimensionSpecialEffects effects,
            ClientLevel level,
            int ticks,
            float partialTick,
            PoseStack poseStack,
            double camX,
            double camY,
            double camZ,
            Matrix4f frustumMatrix,
            Matrix4f projectionMatrix,
            Operation<Boolean> original
    ) {
        if (original.call(
                effects,
                level,
                ticks,
                partialTick,
                poseStack,
                camX,
                camY,
                camZ,
                frustumMatrix,
                projectionMatrix
        )) {
            LocalizedCloudRenderer.clear();
            return true;
        }

        float cloudHeight = effects.getCloudHeight();
        if (ClientWeatherCoordinator.controls(level)
                && WeatherRenderingConfig.settings().enabled()
                && !Float.isNaN(cloudHeight)) {
            LocalizedCloudRenderer.render(
                    level,
                    ticks,
                    partialTick,
                    poseStack,
                    camX,
                    camY,
                    camZ,
                    frustumMatrix,
                    projectionMatrix,
                    cloudHeight
            );
            return true;
        }

        LocalizedCloudRenderer.clear();
        return false;
    }

    /** Replaces vanilla fallback weather quads with per-column local intensity. */
    @WrapOperation(
            method = "renderSnowAndRain(Lnet/minecraft/client/renderer/LightTexture;FDDD)V",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/renderer/DimensionSpecialEffects;renderSnowAndRain(Lnet/minecraft/client/multiplayer/ClientLevel;IFLnet/minecraft/client/renderer/LightTexture;DDD)Z"
            ),
            require = 1
    )
    private boolean wildernessodysseyapi$renderLocalizedPrecipitation(
            DimensionSpecialEffects effects,
            ClientLevel level,
            int ticks,
            float partialTick,
            LightTexture lightTexture,
            double camX,
            double camY,
            double camZ,
            Operation<Boolean> original
    ) {
        if (original.call(effects, level, ticks, partialTick, lightTexture, camX, camY, camZ)) {
            if (ClientWeatherCoordinator.controls(level)) {
                LocalizedPrecipitationRenderer.clearRenderState();
            } else {
                LocalizedPrecipitationRenderer.clear();
            }
            return true;
        }
        if (!ClientWeatherCoordinator.controls(level)) {
            LocalizedPrecipitationRenderer.clear();
            return false;
        }
        LocalizedPrecipitationRenderer.render(
                level,
                ticks,
                partialTick,
                lightTexture,
                camX,
                camY,
                camZ,
                effects.getCloudHeight()
        );
        return true;
    }

    /** Replaces vanilla fallback splash particles and sounds with local samples. */
    @WrapOperation(
            method = "tickRain(Lnet/minecraft/client/Camera;)V",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/renderer/DimensionSpecialEffects;tickRain(Lnet/minecraft/client/multiplayer/ClientLevel;ILnet/minecraft/client/Camera;)Z"
            ),
            require = 1
    )
    private boolean wildernessodysseyapi$tickLocalizedPrecipitation(
            DimensionSpecialEffects effects,
            ClientLevel level,
            int ticks,
            Camera camera,
            Operation<Boolean> original
    ) {
        if (original.call(effects, level, ticks, camera)) {
            if (ClientWeatherCoordinator.controls(level)) {
                LocalizedPrecipitationRenderer.clearTickState();
            } else {
                LocalizedPrecipitationRenderer.clear();
            }
            return true;
        }
        if (!ClientWeatherCoordinator.controls(level)) {
            LocalizedPrecipitationRenderer.clear();
            return false;
        }
        LocalizedPrecipitationRenderer.tick(level, ticks, camera);
        return true;
    }
}
