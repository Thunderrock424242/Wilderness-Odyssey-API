package com.thunder.wildernessodysseyapi.mixin;

import com.thunder.wildernessodysseyapi.weather.client.ClientWeatherCoordinator;
import net.minecraft.client.multiplayer.ClientLevel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Applies localized sky and cloud darkening without changing global weather APIs.
 *
 * <p>The redirects affect only the rain/thunder reads used while calculating
 * sky darkness, sky color, and cloud color. Gameplay and third-party calls to
 * {@link ClientLevel#getRainLevel(float)} still observe vanilla state.</p>
 */
@Mixin(ClientLevel.class)
public abstract class ClientLevelLocalizedWeatherMixin {

    @Redirect(
            method = {"getSkyDarken", "getSkyColor", "getCloudColor"},
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/multiplayer/ClientLevel;getRainLevel(F)F"
            )
    )
    private float wildernessodysseyapi$localizedSkyPrecipitation(
            ClientLevel level,
            float partialTick
    ) {
        if (ClientWeatherCoordinator.controls(level)) {
            return ClientWeatherCoordinator.localSkyDarkening(level);
        }
        return level.getRainLevel(partialTick);
    }

    @Redirect(
            method = {"getSkyDarken", "getSkyColor", "getCloudColor"},
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/multiplayer/ClientLevel;getThunderLevel(F)F"
            )
    )
    private float wildernessodysseyapi$localizedSkyThunder(
            ClientLevel level,
            float partialTick
    ) {
        if (ClientWeatherCoordinator.controls(level)) {
            return ClientWeatherCoordinator.localThunderLevel(level);
        }
        return level.getThunderLevel(partialTick);
    }
}
