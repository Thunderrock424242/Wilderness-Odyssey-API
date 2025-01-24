package com.thunder.wildernessodysseyapi.mixin;

import net.minecraft.world.level.levelgen.structure.Structure;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import static com.thunder.wildernessodysseyapi.MainModClass.WildernessOdysseyAPIMainModClass.LOGGER;

@Mixin(Structure.class)
public class StructureMixin {

    @Inject(method = "place", at = @At("HEAD"))
    private void onStructurePlace(Structure.GenerationContext context, CallbackInfoReturnable<Boolean> cir) {
        String structureName = context.structure().getRegistryName().toString();
        String dimensionName = context.chunkGenerator().getLevel().dimension().location().toString();

        LOGGER.info("Structure '{}' is being placed in dimension '{}'", structureName, dimensionName);
    }
}