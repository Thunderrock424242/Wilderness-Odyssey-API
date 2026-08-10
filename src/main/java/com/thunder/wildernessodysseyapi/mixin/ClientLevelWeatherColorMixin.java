package com.thunder.wildernessodysseyapi.mixin;

import com.thunder.wildernessodysseyapi.riftfall.RiftfallDimensionRules;
import com.thunder.wildernessodysseyapi.temporalrift.registry.TemporalRiftDimensions;
import com.thunder.wildernessodysseyapi.weather.api.PrecipitationIntensity;
import com.thunder.wildernessodysseyapi.weather.api.WeatherSample;
import com.thunder.wildernessodysseyapi.weather.client.ClientWeatherCoordinator;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Tints The Echo's base sky and adds purple color only during Riftfall weather.
 *
 * <p>A return injection is required because Minecraft exposes sky and cloud
 * colors as calculated values rather than through a dedicated color event.</p>
 */
@Mixin(ClientLevel.class)
public class ClientLevelWeatherColorMixin {

    @Inject(method = "getSkyColor", at = @At("RETURN"), cancellable = true)
    private void wildernessodysseyapi$tintSkyColor(Vec3 cameraPos, float partialTick, CallbackInfoReturnable<Vec3> cir) {
        ClientLevel level = (ClientLevel) (Object) this;
        Vec3 result = cir.getReturnValue();
        boolean changed = false;
        if (isEcho(level)) {
            result = blend(result, new Vec3(0.02D, 0.025D, 0.04D), 0.9D);
            changed = true;
        }

        if (shouldApplyRiftfallTint(level)) {
            result = blend(result, new Vec3(0.50D, 0.22D, 0.70D), 0.65D);
            changed = true;
        }
        if (changed) {
            cir.setReturnValue(result);
        }
    }

    @Inject(method = "getCloudColor", at = @At("RETURN"), cancellable = true)
    private void wildernessodysseyapi$tintCloudColor(float partialTick, CallbackInfoReturnable<Vec3> cir) {
        ClientLevel level = (ClientLevel) (Object) this;
        Vec3 result = cir.getReturnValue();
        boolean changed = false;
        if (isEcho(level)) {
            result = blend(result, new Vec3(0.035D, 0.035D, 0.055D), 0.85D);
            changed = true;
        }

        if (shouldApplyRiftfallTint(level)) {
            result = blend(result, new Vec3(0.58D, 0.26D, 0.79D), 0.75D);
            changed = true;
        }
        if (changed) {
            cir.setReturnValue(result);
        }
    }

    private static boolean shouldApplyRiftfallTint(ClientLevel level) {
        if (ClientWeatherCoordinator.controls(level)) {
            WeatherSample sample = ClientWeatherCoordinator.localSample(level);
            return RiftfallDimensionRules.permitsStormVisuals(
                    level.dimension(),
                    PrecipitationIntensity.isFunctional(sample.precipitationIntensity()),
                    sample.thunderIntensity() >= 0.35
            );
        }
        return RiftfallDimensionRules.permitsStormVisuals(
                level.dimension(),
                level.isRaining(),
                level.isThundering()
        );
    }

    private static boolean isEcho(ClientLevel level) {
        return level.dimension().equals(TemporalRiftDimensions.THE_ECHO_KEY);
    }

    private static Vec3 blend(Vec3 base, Vec3 tint, double factor) {
        return new Vec3(
                base.x * (1.0D - factor) + tint.x * factor,
                base.y * (1.0D - factor) + tint.y * factor,
                base.z * (1.0D - factor) + tint.z * factor
        );
    }
}
