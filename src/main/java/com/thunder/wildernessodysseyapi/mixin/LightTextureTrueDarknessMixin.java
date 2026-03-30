package com.thunder.wildernessodysseyapi.mixin;

import com.thunder.wildernessodysseyapi.config.TrueDarknessConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.level.dimension.DimensionType;
import net.minecraft.client.renderer.LightTexture;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LightTexture.class)
/**
 * Client lightmap mixin that deepens nighttime lighting when True Darkness is enabled.
 */
public class LightTextureTrueDarknessMixin {

    @Inject(method = "getBrightness", at = @At("RETURN"), cancellable = true)
    private static void wildernessodysseyapi$darkenLightmapAtNight(
            DimensionType dimensionType,
            int lightLevel,
            CallbackInfoReturnable<Float> cir
    ) {
        if (!TrueDarknessConfig.ENABLED.get() || !dimensionType.hasSkyLight()) {
            return;
        }

        Minecraft minecraft = Minecraft.getInstance();
        ClientLevel level = minecraft.level;
        if (level == null) {
            return;
        }

        float nightFactor = getNightFactor(level.getDayTime());
        if (nightFactor <= 0.0F) {
            return;
        }

        float baseBrightness = cir.getReturnValue();
        float strength = TrueDarknessConfig.DARKNESS_STRENGTH.get().floatValue();
        float minNightBrightness = TrueDarknessConfig.MIN_NIGHT_BRIGHTNESS.get().floatValue();
        float moonlightInfluence = TrueDarknessConfig.MOONLIGHT_INFLUENCE.get().floatValue();

        float lightLevelFactor = 1.0F - (lightLevel / 15.0F);
        float moonVisibility = getMoonVisibility(level.getMoonPhase());
        float moonCompensation = moonVisibility * moonlightInfluence * 0.35F * nightFactor;

        float reduction = strength * nightFactor * lightLevelFactor;
        float darkened = baseBrightness * (1.0F - reduction);

        float minimum = minNightBrightness + moonCompensation;
        cir.setReturnValue(Mth.clamp(Math.max(darkened, minimum), 0.0F, 1.0F));
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
