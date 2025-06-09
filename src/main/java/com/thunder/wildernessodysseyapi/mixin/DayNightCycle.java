package com.thunder.wildernessodysseyapi.mixin;

import net.minecraft.server.level.ServerLevel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ServerLevel.class)
public abstract class DayNightCycle {


    private long dayTime;

    @Shadow
    public abstract void setDayTime(long time);

    private static final long TOTAL_CYCLE_TIME = 72000L; // 1 Hour in Ticks
    private static final long DAY_DURATION = 36000L;     // 30 Min (Day)
    private static final long NIGHT_DURATION = 24000L;   // 20 Min (Night)
    private static final long SUNSET_DURATION = 6000L;   // 5 Min (Sunset)
    private static final long SUNRISE_DURATION = 6000L;  // 5 Min (Sunrise)

    @Inject(method = "tick", at = @At("HEAD"))
    private void modifyDayNightCycle(CallbackInfo ci) {
        ServerLevel world = (ServerLevel) (Object) this;
        long currentTime = world.getDayTime() % TOTAL_CYCLE_TIME;

        if (currentTime < DAY_DURATION) {
            world.setDayTime(world.getDayTime() + 2); // Slow down daytime progression
        } else if (currentTime < DAY_DURATION + SUNSET_DURATION) {
            world.setDayTime(world.getDayTime() + 1); // Sunset - Normal speed
        } else if (currentTime < DAY_DURATION + SUNSET_DURATION + NIGHT_DURATION) {
            world.setDayTime(world.getDayTime() + 1); // Night - Normal speed
        } else if (currentTime < DAY_DURATION + SUNSET_DURATION + NIGHT_DURATION + SUNRISE_DURATION) {
            world.setDayTime(world.getDayTime() + 1); // Sunrise - Normal speed
        }
    }
}
