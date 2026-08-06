package com.thunder.wildernessodysseyapi.mixin;

import com.llamalad7.mixinextras.sugar.Local;
import com.thunder.wildernessodysseyapi.watersystem.water.render.WaterHandoffBuildOutput;
import com.thunder.wildernessodysseyapi.watersystem.water.render.WaterRenderCoordinator;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.ArrayList;
import java.util.List;

/** Acknowledges only filtered Sodium builds after synchronous region upload. */
@Pseudo
@Mixin(
        targets = "net.caffeinemc.mods.sodium.client.render.chunk.RenderSectionManager",
        remap = false
)
public abstract class SodiumSectionUploadHandoffMixin {

    /** Sodium 0.8.12 uploads the filtered list before publishing section info. */
    @Inject(
            method = "processChunkBuildResults(Ljava/util/ArrayList;)Z",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/caffeinemc/mods/sodium/client/render/chunk/region/RenderRegionManager;"
                            + "uploadResults("
                            + "Lnet/caffeinemc/mods/sodium/client/gl/device/CommandList;"
                            + "Ljava/util/Collection;)V",
                    shift = At.Shift.AFTER
            ),
            remap = false,
            require = 1
    )
    private void wildernessOdysseyApi$acknowledgeWaterHandoff(
            ArrayList<?> rawResults,
            CallbackInfoReturnable<Boolean> callbackInfo,
            @Local(index = 2) List<?> acceptedResults
    ) {
        for (Object output : acceptedResults) {
            if (output instanceof WaterHandoffBuildOutput taggedOutput) {
                WaterRenderCoordinator.acknowledgeSectionUpload(
                        taggedOutput.wildernessOdysseyApi$getWaterHandoffReceipt());
            }
        }
    }
}
