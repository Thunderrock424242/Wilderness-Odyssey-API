package com.thunder.wildernessodysseyapi.mixin;

import com.thunder.ticktoklib.TickTokHelper;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Level.class)
public abstract class DayNightCycleMixin {

    @Unique private static final long TOTAL_CYCLE      = TickTokHelper.duration(1, 0, 0); // 1 hour day
    @Unique private static final long DAY_DURATION     = TickTokHelper.duration(0, 30, 0); // 30 min
    @Unique private static final long SUNSET_DURATION  = TickTokHelper.duration(0, 5, 0);  // 5 min
    @Unique private static final long NIGHT_DURATION   = TickTokHelper.duration(0, 20, 0); // 20 min
    @Unique private static final long SUNRISE_DURATION = TickTokHelper.duration(0, 5, 0);  // 5 min

    @Inject(method = "advanceDaytime", at = @At("HEAD"), cancellable = true)
    private void overrideAdvanceDaytime(CallbackInfoReturnable<Long> cir) {
        Level self = (Level)(Object)this;
        long t = self.getDayTime();
        long phase = t % TOTAL_CYCLE;

        long dayEnd     = DAY_DURATION;
        long sunsetEnd  = dayEnd + SUNSET_DURATION;
        long nightEnd   = sunsetEnd + NIGHT_DURATION;
        long sunriseEnd = nightEnd + SUNRISE_DURATION;

        long delta;
        if      (phase < dayEnd)       delta = 2L; // slower daylight
        else if (phase < sunsetEnd)    delta = 1L;
        else if (phase < nightEnd)     delta = 1L;
        else if (phase < sunriseEnd)   delta = 1L;
        else                           delta = 1L;

        cir.setReturnValue(delta);
    }
}