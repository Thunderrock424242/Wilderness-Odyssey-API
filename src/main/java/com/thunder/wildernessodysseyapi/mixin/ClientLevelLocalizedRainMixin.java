package com.thunder.wildernessodysseyapi.mixin;

import com.thunder.wildernessodysseyapi.weather.api.PrecipitationType;
import com.thunder.wildernessodysseyapi.weather.api.PrecipitationIntensity;
import com.thunder.wildernessodysseyapi.weather.client.ClientWeatherCoordinator;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Keeps client prediction and rain-sensitive visuals aligned with server-local rain. */
@Mixin(Level.class)
public abstract class ClientLevelLocalizedRainMixin {

    /** Replaces the global client rain flag only while a local snapshot owns weather. */
    @Inject(method = "isRainingAt", at = @At("HEAD"), cancellable = true)
    private void wildernessodysseyapi$useLocalizedClientRain(
            BlockPos position,
            CallbackInfoReturnable<Boolean> callback
    ) {
        if (!((Object) this instanceof ClientLevel level)
                || !ClientWeatherCoordinator.controls(level)) {
            return;
        }
        boolean raining = ClientWeatherCoordinator.currentPrecipitationTypeAt(level, position)
                == PrecipitationType.RAIN
                && PrecipitationIntensity.isFunctional(
                        ClientWeatherCoordinator.currentPrecipitationIntensityAt(level, position)
                )
                && level.canSeeSky(position)
                && level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING, position).getY()
                <= position.getY();
        callback.setReturnValue(raining);
    }
}
