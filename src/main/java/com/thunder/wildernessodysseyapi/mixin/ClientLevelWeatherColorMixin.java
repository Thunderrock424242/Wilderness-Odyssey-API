package com.thunder.wildernessodysseyapi.mixin;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ClientLevel.class)
public class ClientLevelWeatherColorMixin {

    @Inject(method = "getSkyColor", at = @At("RETURN"), cancellable = true)
    private void wildernessodysseyapi$tintSkyColor(Vec3 cameraPos, float partialTick, CallbackInfoReturnable<Vec3> cir) {
        ClientLevel level = (ClientLevel) (Object) this;
        if (!level.isRaining() || !level.isThundering()) {
            return;
        }

        Vec3 original = cir.getReturnValue();
        Vec3 tint = new Vec3(0.50D, 0.22D, 0.70D);
        cir.setReturnValue(blend(original, tint, 0.65D));
    }

    @Inject(method = "getCloudColor", at = @At("RETURN"), cancellable = true)
    private void wildernessodysseyapi$tintCloudColor(float partialTick, CallbackInfoReturnable<Vec3> cir) {
        ClientLevel level = (ClientLevel) (Object) this;
        if (!level.isRaining() || !level.isThundering()) {
            return;
        }

        Vec3 original = cir.getReturnValue();
        Vec3 tint = new Vec3(0.58D, 0.26D, 0.79D);
        cir.setReturnValue(blend(original, tint, 0.75D));
    }

    private static Vec3 blend(Vec3 base, Vec3 tint, double factor) {
        return new Vec3(
                base.x * (1.0D - factor) + tint.x * factor,
                base.y * (1.0D - factor) + tint.y * factor,
                base.z * (1.0D - factor) + tint.z * factor
        );
    }
}
