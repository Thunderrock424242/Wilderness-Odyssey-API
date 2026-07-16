package com.thunder.wildernessodysseyapi.mixin;

import com.thunder.wildernessodysseyapi.weather.api.PrecipitationType;
import com.thunder.wildernessodysseyapi.weather.client.ClientWeatherCoordinator;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.biome.Biome;
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
