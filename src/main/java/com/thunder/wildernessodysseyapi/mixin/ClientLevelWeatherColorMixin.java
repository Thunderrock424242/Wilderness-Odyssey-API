package com.thunder.wildernessodysseyapi.mixin;

import com.thunder.wildernessodysseyapi.temporalrift.registry.TemporalRiftDimensions;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ClientLevel.class)
/**
 * Client-level mixin that tints world colors during Riftfall weather effects.
 */
public class ClientLevelWeatherColorMixin {

    @Inject(method = "getSkyColor", at = @At("RETURN"), cancellable = true)
    private void wildernessodysseyapi$tintSkyColor(Vec3 cameraPos, float partialTick, CallbackInfoReturnable<Vec3> cir) {
        ClientLevel level = (ClientLevel) (Object) this;
        if (isEcho(level)) {
            cir.setReturnValue(blend(cir.getReturnValue(), new Vec3(0.02D, 0.025D, 0.04D), 0.9D));
            return;
        }

        if (!shouldApplyRiftfallTint(level)) {
            return;
        }

        Vec3 original = cir.getReturnValue();
        Vec3 tint = new Vec3(0.50D, 0.22D, 0.70D);
        cir.setReturnValue(blend(original, tint, 0.65D));
    }

    @Inject(method = "getCloudColor", at = @At("RETURN"), cancellable = true)
    private void wildernessodysseyapi$tintCloudColor(float partialTick, CallbackInfoReturnable<Vec3> cir) {
        ClientLevel level = (ClientLevel) (Object) this;
        if (isEcho(level)) {
            cir.setReturnValue(blend(cir.getReturnValue(), new Vec3(0.035D, 0.035D, 0.055D), 0.85D));
            return;
        }

        if (!shouldApplyRiftfallTint(level)) {
            return;
        }

        Vec3 original = cir.getReturnValue();
        Vec3 tint = new Vec3(0.58D, 0.26D, 0.79D);
        cir.setReturnValue(blend(original, tint, 0.75D));
    }

    private static boolean shouldApplyRiftfallTint(ClientLevel level) {
        return level.isRaining() && level.isThundering();
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
