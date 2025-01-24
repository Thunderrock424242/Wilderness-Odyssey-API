package com.thunder.wildernessodysseyapi.mixin;

import net.minecraft.world.level.biome.Biome;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import static com.thunder.wildernessodysseyapi.MainModClass.WildernessOdysseyAPIMainModClass.LOGGER;

@Mixin(Biome.class)
public class BiomeMixin {

    @Inject(method = "build", at = @At("HEAD"))
    private void onBiomeBuild(CallbackInfoReturnable<Biome> cir) {
        LOGGER.info("Biome '{}' is being built.", this.getClass().getSimpleName());
    }
}