package com.thunder.wildernessodysseyapi.mixin;

import com.thunder.wildernessodysseyapi.temporalrift.client.ClientEchoState;
import net.minecraft.client.Minecraft;
import net.minecraft.world.level.dimension.DimensionType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/**
 * Optional local celestial pauses need the dimension's time-of-day input; no event exposes it.
 * Installed only on clients and gated to the active client dimension on the render thread,
 * so integrated-server clocks and other dimensions retain their original input.
 */
@Mixin(DimensionType.class)
public abstract class EchoCelestialTimeMixin {
    @ModifyVariable(method = "timeOfDay", at = @At("HEAD"), argsOnly = true)
    private long wildernessodysseyapi$echoCelestialTime(long dayTime) {
        Minecraft minecraft = Minecraft.getInstance();
        if (!minecraft.isSameThread() || minecraft.level == null
                || minecraft.level.dimensionType() != (Object) this) return dayTime;
        return ClientEchoState.visualDayTime(dayTime);
    }
}
