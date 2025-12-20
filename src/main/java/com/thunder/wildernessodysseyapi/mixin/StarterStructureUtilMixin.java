package com.thunder.wildernessodysseyapi.mixin;

import com.natamus.starterstructure_common_neoforge.util.Util;
import com.thunder.wildernessodysseyapi.WorldGen.structure.StarterStructureTerrainAdapter;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = Util.class, remap = false)
public class StarterStructureUtilMixin {
    @Inject(method = "generateSchematic", at = @At("RETURN"))
    private static void wildernessOdysseyApi$triggerTerrainReplacer(ServerLevel serverLevel, CallbackInfoReturnable<BlockPos> cir) {
        BlockPos structureOrigin = cir.getReturnValue();
        if (structureOrigin != null) {
            StarterStructureTerrainAdapter.scheduleTerrainReplacement(serverLevel, structureOrigin);
        }
    }
}
