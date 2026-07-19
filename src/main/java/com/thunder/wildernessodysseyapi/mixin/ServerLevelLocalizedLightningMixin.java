package com.thunder.wildernessodysseyapi.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.thunder.wildernessodysseyapi.weather.config.WeatherConfig;
import net.minecraft.server.level.ServerLevel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Prevents vanilla global thunderstorms from creating out-of-region strikes.
 *
 * <p>NeoForge has no event around only the natural lightning block inside
 * {@code ServerLevel#tickChunk}. Wrapping its single thunder query leaves the
 * global rain state available to compatibility consumers while transferring
 * natural-strike ownership to the server-localized scheduler.</p>
 */
@Mixin(ServerLevel.class)
public abstract class ServerLevelLocalizedLightningMixin {

    /** Disables only vanilla's chunk-random natural strike gate. */
    @WrapOperation(
            method = "tickChunk(Lnet/minecraft/world/level/chunk/LevelChunk;I)V",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/server/level/ServerLevel;isThundering()Z"
            ),
            require = 1
    )
    private boolean wildernessodysseyapi$suppressGlobalNaturalLightning(
            ServerLevel level,
            Operation<Boolean> original
    ) {
        if (WeatherConfig.dimensionEnabled(level.dimension())) {
            return false;
        }
        return original.call(level);
    }
}
