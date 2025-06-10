package com.thunder.wildernessodysseyapi.mixin;

import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Level.class)
public abstract class DayNightCycleMixin {
    @Unique private static final long TOTAL_CYCLE      = 72_000L;
    @Unique private static final long DAY_DURATION     = 36_000L;
    @Unique private static final long SUNSET_DURATION  =  6_000L;
    @Unique private static final long NIGHT_DURATION   = 24_000L;
    @Unique private static final long SUNRISE_DURATION =  6_000L;

    @Inject(method = "advanceDaytime", at = @At("HEAD"), cancellable  = true
    )
    private void overrideAdvanceDaytime(CallbackInfoReturnable<Long> cir) {
        Level self = (Level)(Object)this;
        long t       = self.getDayTime();
        long phase   = t % TOTAL_CYCLE;

        long dayEnd     = DAY_DURATION;
        long sunsetEnd  = dayEnd   + SUNSET_DURATION;
        long nightEnd   = sunsetEnd + NIGHT_DURATION;
        long sunriseEnd = nightEnd  + SUNRISE_DURATION;  // <— now we use SUNRISE_DURATION

        long delta;
        if      (phase < dayEnd)       delta = 2L;  // daytime slower
        else if (phase < sunsetEnd)    delta = 1L;  // sunset
        else if (phase < nightEnd)     delta = 1L;  // night
        else if (phase < sunriseEnd)   delta = 1L;  // **sunrise** (references SUNRISE_DURATION)
        else                           delta = 1L;  // wrap-around (should never happen)

        cir.setReturnValue(delta);
    }
}