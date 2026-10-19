package com.thunder.wildernessodysseyapi.mixin;

import com.thunder.wildernessodysseyapi.core.ModRegistries;
import com.thunder.wildernessodysseyapi.watersystem.water.worldgen.river.RiverWorldgenConfig;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** No event exposes the boundary before structures and surface decoration mutate this terrain. */
@Mixin(ChunkGenerator.class)
public abstract class RiverGenerationLifecycleMixin {
    @SuppressWarnings("DataFlowIssue") // The transformed receiver is the ChunkGenerator target.
    @Inject(method = "applyBiomeDecoration", at = @At("HEAD"))
    private void wildernessOdyssey$generateRivers(WorldGenLevel level, ChunkAccess chunk,
                                                 StructureManager structures, CallbackInfo ci) {
        if (!RiverWorldgenConfig.enabled()) return;
        ModRegistries.RIVER_NETWORK_FEATURE.get().place(NoneFeatureConfiguration.INSTANCE, level,
                (ChunkGenerator) (Object) this, level.getRandom(), chunk.getPos().getWorldPosition());
    }
}
