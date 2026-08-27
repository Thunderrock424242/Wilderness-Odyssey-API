package com.thunder.wildernessodysseyapi.mixin;

import com.thunder.wildernessodysseyapi.cinematic.client.CinematicClientController;
import net.minecraft.client.Camera;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Adds world-space camera tracks only while a client cinematic explicitly supplies one. */
@Mixin(Camera.class)
public abstract class CameraCinematicMixin {
    @Shadow
    protected abstract void setPosition(Vec3 position);

    @Inject(method = "setup", at = @At("TAIL"))
    private void wildernessOdyssey$applyCinematicPosition(
            BlockGetter level,
            Entity entity,
            boolean detached,
            boolean mirrored,
            float partialTick,
            CallbackInfo callbackInfo
    ) {
        CinematicClientController.get().cameraPosition(partialTick).ifPresent(this::setPosition);
    }
}
