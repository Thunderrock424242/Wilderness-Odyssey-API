package com.thunder.wildernessodysseyapi.mixin;

import com.thunder.wildernessodysseyapi.temporalrift.echo.structure.EchoStructurePolicy;
import net.minecraft.core.HolderLookup;
import net.minecraft.server.level.ChunkMap;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.structure.StructureSet;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

/**
 * Excludes explicitly Echo-only sets from Earth at structure-state construction. There is no
 * dimension-aware structure-set event; biome modifiers cannot separate these shared biome sources.
 * In 1.21.1 ChunkMap assigns level before this sole createState call. Other dimensions are untouched.
 */
@Mixin(ChunkMap.class)
public abstract class EarthStructurePolicyMixin {
    @Shadow @Final private ServerLevel level;

    @ModifyArg(method = "<init>", at = @At(value = "INVOKE", target =
            "Lnet/minecraft/world/level/chunk/ChunkGenerator;createState(Lnet/minecraft/core/HolderLookup;Lnet/minecraft/world/level/levelgen/RandomState;J)Lnet/minecraft/world/level/chunk/ChunkGeneratorStructureState;"), index = 0)
    private HolderLookup<StructureSet> wildernessodysseyapi$earthStructureSets(HolderLookup<StructureSet> original) {
        return level.dimension().equals(Level.OVERWORLD) ? EchoStructurePolicy.forDimension(original, false) : original;
    }
}
