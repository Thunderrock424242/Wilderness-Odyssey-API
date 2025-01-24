package com.thunder.wildernessodysseyapi.mixin;

import net.minecraft.world.level.dimension.DimensionType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import static com.thunder.wildernessodysseyapi.MainModClass.WildernessOdysseyAPIMainModClass.LOGGER;

@Mixin(DimensionType.class)
public class DimensionLoadMixin {

    @Inject(method = "create", at = @At("HEAD"))
    private void onDimensionCreate(CallbackInfo ci) {
        LOGGER.info("Dimension '{}' is being loaded.", this.getClass().getSimpleName());
    }
}