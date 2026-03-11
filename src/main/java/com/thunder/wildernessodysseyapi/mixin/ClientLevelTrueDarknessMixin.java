package com.thunder.wildernessodysseyapi.mixin;

import com.thunder.wildernessodysseyapi.config.TrueDarknessConfig;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.util.Mth;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ClientLevel.class)
public class ClientLevelTrueDarknessMixin {

    @Inject(method = "getSkyDarken", at = @At("RETURN"), cancellable = true)
    private void wildernessodysseyapi$applyTrueDarkness(float partialTick, CallbackInfoReturnable<Float> cir) {
        if (!TrueDarknessConfig.ENABLED.get()) {
            return;
        }

        ClientLevel level = (ClientLevel) (Object) this;
        if (!level.dimensionType().hasSkyLight()) {
            return;
        }

        float nightFactor = getNightFactor(level.getDayTime());
        if (nightFactor <= 0.0F) {
            return;
        }

        float baseDarken = cir.getReturnValue();
        float strength = TrueDarknessConfig.DARKNESS_STRENGTH.get().floatValue();
        float moonlightInfluence = TrueDarknessConfig.MOONLIGHT_INFLUENCE.get().floatValue();

        float moonVisibility = getMoonVisibility(level.getMoonPhase());
        float moonlightLift = moonVisibility * moonlightInfluence * 0.35F;

        float boostedDarken = baseDarken + (strength * nightFactor);
        boostedDarken -= moonlightLift * nightFactor;

        cir.setReturnValue(Mth.clamp(boostedDarken, 0.0F, 1.0F));
    }

    private static float getNightFactor(long gameTime) {
        long dayTime = Math.floorMod(gameTime, 24000L);
        if (dayTime < 12000L || dayTime > 23000L) {
            return 0.0F;
        }

        if (dayTime <= 18000L) {
            return (dayTime - 12000L) / 6000.0F;
        }

        return (23000L - dayTime) / 5000.0F;
    }

    private static float getMoonVisibility(int moonPhase) {
        return switch (moonPhase) {
            case 0 -> 1.0F;
            case 1, 7 -> 0.8F;
            case 2, 6 -> 0.55F;
            case 3, 5 -> 0.3F;
            default -> 0.1F;
        };
    }
}
