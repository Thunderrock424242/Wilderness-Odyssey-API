package com.thunder.wildernessodysseyapi.mixin;

import com.thunder.wildernessodysseyapi.watersystem.water.worldgen.GenerationWaterStateMapper;
import net.minecraft.world.level.levelgen.feature.SpringFeature;
import net.minecraft.world.level.levelgen.feature.configurations.SpringConfiguration;
import net.minecraft.world.level.material.FluidState;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Maps a water spring's configured state for both placement and its scheduled
 * fluid tick. The ProtoChunk hook maps the stored block, but cannot by itself
 * change the fluid object that vanilla schedules immediately afterward.
 */
@Mixin(SpringFeature.class)
public abstract class SpringFeatureWaterMixin {

    /** Returns the custom equivalent only for exact vanilla water configurations. */
    @Redirect(
            method = "place",
            at = @At(
                    value = "FIELD",
                    target = "Lnet/minecraft/world/level/levelgen/feature/configurations/SpringConfiguration;state:Lnet/minecraft/world/level/material/FluidState;",
                    opcode = Opcodes.GETFIELD
            ),
            require = 2
    )
    private FluidState wildernessOdyssey$mapSpringWater(SpringConfiguration configuration) {
        return GenerationWaterStateMapper.mapFluid(configuration.state);
    }
}
