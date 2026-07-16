package com.thunder.wildernessodysseyapi.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.blaze3d.vertex.PoseStack;
import com.thunder.wildernessodysseyapi.weather.api.PrecipitationType;
import com.thunder.wildernessodysseyapi.weather.client.ClientWeatherCoordinator;
import com.thunder.wildernessodysseyapi.weather.client.cloud.LocalizedCloudRenderer;
import com.thunder.wildernessodysseyapi.weather.config.WeatherRenderingConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.DimensionSpecialEffects;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.biome.Biome;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Routes vanilla precipitation quads, particles, and sounds through localized weather.
 *
 * <p>NeoForge does not expose an event for replacing the rain value and biome
 * precipitation classification inside {@code renderSnowAndRain}/{@code tickRain}.
 * These redirects are therefore limited to those two methods; global
 * {@link ClientLevel} weather getters remain untouched for compatibility.</p>
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

    @Redirect(
            method = "renderSnowAndRain",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/multiplayer/ClientLevel;getRainLevel(F)F"
            )
    )
    private float wildernessodysseyapi$localizedRenderedIntensity(
            ClientLevel level,
            float partialTick
    ) {
        return localizedIntensityOrVanilla(level, partialTick);
    }

    @Redirect(
            method = "tickRain",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/multiplayer/ClientLevel;getRainLevel(F)F"
            )
    )
    private float wildernessodysseyapi$localizedParticleAndSoundIntensity(
            ClientLevel level,
            float partialTick
    ) {
        return localizedIntensityOrVanilla(level, partialTick);
    }

    @Redirect(
            method = "renderSnowAndRain",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/level/biome/Biome;hasPrecipitation()Z"
            )
    )
    private boolean wildernessodysseyapi$allowLocalizedPrecipitationBiome(Biome biome) {
        ClientLevel level = Minecraft.getInstance().level;
        return ClientWeatherCoordinator.controls(level) || biome.hasPrecipitation();
    }

    @Redirect(
            method = "renderSnowAndRain",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/level/biome/Biome;getPrecipitationAt(Lnet/minecraft/core/BlockPos;)Lnet/minecraft/world/level/biome/Biome$Precipitation;"
            )
    )
    private Biome.Precipitation wildernessodysseyapi$localizedRenderedType(
            Biome biome,
            BlockPos pos
    ) {
        return localizedTypeOrBiome(biome, pos);
    }

    @Redirect(
            method = "tickRain",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/level/biome/Biome;getPrecipitationAt(Lnet/minecraft/core/BlockPos;)Lnet/minecraft/world/level/biome/Biome$Precipitation;"
            )
    )
    private Biome.Precipitation wildernessodysseyapi$localizedParticleAndSoundType(
            Biome biome,
            BlockPos pos
    ) {
        return localizedTypeOrBiome(biome, pos);
    }

    private static float localizedIntensityOrVanilla(ClientLevel level, float partialTick) {
        if (ClientWeatherCoordinator.controls(level)) {
            return ClientWeatherCoordinator.localPrecipitationIntensity(level);
        }
        return level.getRainLevel(partialTick);
    }

    private static Biome.Precipitation localizedTypeOrBiome(Biome biome, BlockPos pos) {
        ClientLevel level = Minecraft.getInstance().level;
        if (!ClientWeatherCoordinator.controls(level)) {
            return biome.getPrecipitationAt(pos);
        }

        PrecipitationType type = ClientWeatherCoordinator.precipitationTypeAt(level, pos);
        return switch (type) {
            case NONE -> Biome.Precipitation.NONE;
            case RAIN -> Biome.Precipitation.RAIN;
            case SNOW -> Biome.Precipitation.SNOW;
        };
    }
}
