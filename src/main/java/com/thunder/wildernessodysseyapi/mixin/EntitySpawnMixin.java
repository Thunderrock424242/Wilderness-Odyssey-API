package com.thunder.wildernessodysseyapi.mixin;

import net.minecraft.world.entity.EntityType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import static com.thunder.wildernessodysseyapi.MainModClass.WildernessOdysseyAPIMainModClass.LOGGER;

@Mixin(EntityType.class)
public class EntitySpawnMixin {

    @Inject(method = "spawn", at = @At("HEAD"))
    private void onEntitySpawn(CallbackInfo ci) {
        LOGGER.info("Entity spawned: {}", this.getClass().getSimpleName());
    }
}