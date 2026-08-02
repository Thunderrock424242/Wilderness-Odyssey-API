package com.thunder.wildernessodysseyapi.mixin;

import com.thunder.wildernessodysseyapi.watersystem.water.compat.vanilla.StructureWaterMarkerAdapter;
import com.thunder.wildernessodysseyapi.watersystem.water.config.WaterSimulationConfig;
import com.thunder.wildernessodysseyapi.watersystem.water.config.WildernessWaterRules;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;

/** Converts explicit water markers once, after normal structure processors run. */
@Mixin(StructureTemplate.class)
public abstract class StructureTemplateWaterMarkerMixin {

    /**
     * Rewrites the processed placement list rather than scanning placed blocks.
     * This preserves rotation, mirrors, integrity processors, and template data.
     */
    @Inject(
            method = "processBlockInfos(Lnet/minecraft/world/level/ServerLevelAccessor;"
                    + "Lnet/minecraft/core/BlockPos;Lnet/minecraft/core/BlockPos;"
                    + "Lnet/minecraft/world/level/levelgen/structure/templatesystem/StructurePlaceSettings;"
                    + "Ljava/util/List;"
                    + "Lnet/minecraft/world/level/levelgen/structure/templatesystem/StructureTemplate;)"
                    + "Ljava/util/List;",
            at = @At("RETURN"),
            cancellable = true
    )
    private static void wildernessOdysseyApi$convertWaterMarkers(
            ServerLevelAccessor level,
            BlockPos placementOrigin,
            BlockPos structureOrigin,
            StructurePlaceSettings settings,
            List<StructureTemplate.StructureBlockInfo> source,
            StructureTemplate template,
            CallbackInfoReturnable<List<StructureTemplate.StructureBlockInfo>> callbackInfo
    ) {
        if (!WaterSimulationConfig.structureWaterMarkersEnabled()
                || !WildernessWaterRules.isEnabled(level.getLevel())) {
            return;
        }
        callbackInfo.setReturnValue(StructureWaterMarkerAdapter.convertMarkers(callbackInfo.getReturnValue()));
    }
}
