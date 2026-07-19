package com.thunder.wildernessodysseyapi.mixin;

import com.thunder.wildernessodysseyapi.weather.config.WeatherConfig;
import com.thunder.wildernessodysseyapi.weather.integration.LocalizedPrecipitationController;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Routes vanilla server rain exposure through the localized weather authority.
 *
 * <p>{@link Level#isRainingAt(BlockPos)} is vanilla's shared choke point for
 * fire, farmland, fishing, entity wetness, Riptide, and rain-sensitive mobs.
 * NeoForge exposes no equivalent position-aware event, so this injection owns
 * only enabled server dimensions and otherwise preserves the original result.</p>
 */
@Mixin(Level.class)
public abstract class LevelLocalizedRainMixin {

    /** Supplies authoritative local rain before vanilla consults its global flag. */
    @Inject(method = "isRainingAt", at = @At("HEAD"), cancellable = true)
    private void wildernessodysseyapi$useLocalizedServerRain(
            BlockPos position,
            CallbackInfoReturnable<Boolean> callback
    ) {
        if ((Object) this instanceof ServerLevel level
                && WeatherConfig.dimensionEnabled(level.dimension())) {
            callback.setReturnValue(LocalizedPrecipitationController.get().isRainingAt(level, position));
        }
    }
}
