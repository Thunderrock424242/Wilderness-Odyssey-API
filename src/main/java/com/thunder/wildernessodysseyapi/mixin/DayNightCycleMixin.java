package com.thunder.wildernessodysseyapi.mixin;

import com.thunder.ticktoklib.TickTokHelper;
import net.minecraft.server.level.ServerLevel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Customizes the day-night cycle using configurable TickTokLib durations.
 * Replaces need for custom advanceDaytime logic.
 */
@Mixin(ServerLevel.class)
public abstract class DayNightCycleMixin {

    // Total custom cycle duration
    @Unique
    private static final long TOTAL_CYCLE      = TickTokHelper.duration(1, 0, 0); // 1 real hour
    @Unique
    private static final long DAY_DURATION     = TickTokHelper.duration(0, 30, 0); // 30 min
    @Unique
    private static final long SUNSET_DURATION  = TickTokHelper.duration(0, 5, 0);  // 5 min
    @Unique
    private static final long NIGHT_DURATION   = TickTokHelper.duration(0, 20, 0); // 20 min
    @Unique
    private static final long SUNRISE_DURATION = TickTokHelper.duration(0, 5, 0);  // 5 min

    @Unique
    private static final long VANILLA_DAY_TICKS = 24000L;

    @Inject(method = "getDayTimePerTick", at = @At("HEAD"), cancellable = true)
    private void overridePerTickSpeed(CallbackInfoReturnable<Float> cir) {
        ServerLevel level = (ServerLevel)(Object)this;
        long timeOfDay = level.getDayTime() % TOTAL_CYCLE;

        long dayEnd     = DAY_DURATION;
        long sunsetEnd  = dayEnd + SUNSET_DURATION;
        long nightEnd   = sunsetEnd + NIGHT_DURATION;
        long sunriseEnd = nightEnd + SUNRISE_DURATION;

        float rate;

        if (timeOfDay < dayEnd) {
            rate = VANILLA_DAY_TICKS / (float)TOTAL_CYCLE; // Base speed
        } else if (timeOfDay < sunsetEnd) {
            rate = 1.5f * VANILLA_DAY_TICKS / TOTAL_CYCLE; // Sunset = faster
        } else if (timeOfDay < nightEnd) {
            rate = 0.5f * VANILLA_DAY_TICKS / TOTAL_CYCLE; // Night = slower
        } else if (timeOfDay < sunriseEnd) {
            rate = 1.0f * VANILLA_DAY_TICKS / TOTAL_CYCLE; // Sunrise = normal
        } else {
            rate = (float) VANILLA_DAY_TICKS / TOTAL_CYCLE;
        }

        cir.setReturnValue(rate);
    }
}