package com.thunder.wildernessodysseyapi.mixin;

import com.thunder.wildernessodysseyapi.watersystem.water.render.WaterHandoffBuildOutput;
import com.thunder.wildernessodysseyapi.watersystem.water.render.WaterHandoffReceipt;
import com.thunder.wildernessodysseyapi.watersystem.water.render.WaterRenderCoordinator;
import com.thunder.wildernessodysseyapi.watersystem.water.render.WaterRendererSectionCoordinates;
import net.minecraft.core.SectionPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Tags the exact legacy Embeddium meshing result that removed fallback tops. */
@Pseudo
@Mixin(
        targets = "org.embeddedt.embeddium.impl.render.chunk.compile.tasks.ChunkBuilderMeshingTask",
        remap = false
)
public class EmbeddiumChunkMeshingHandoffMixin {

    @Unique
    private long wildernessOdysseyApi$waterSectionKey = Long.MIN_VALUE;

    /** Captures section coordinates without a compile-time Embeddium dependency. */
    @Inject(method = "<init>", at = @At("RETURN"), remap = false, require = 1)
    private void wildernessOdysseyApi$captureWaterSection(
            @Coerce Object renderSection,
            @Coerce Object renderContext,
            int buildTime,
            CallbackInfo callbackInfo
    ) {
        WaterRendererSectionCoordinates coordinates =
                (WaterRendererSectionCoordinates) renderSection;
        wildernessOdysseyApi$waterSectionKey = SectionPos.asLong(
                coordinates.wildernessOdysseyApi$sectionX(),
                coordinates.wildernessOdysseyApi$sectionY(),
                coordinates.wildernessOdysseyApi$sectionZ()
        );
    }

    /** Starts a fresh per-thread ownership-mask observation. */
    @Inject(
            method = "execute("
                    + "Lorg/embeddedt/embeddium/impl/render/chunk/compile/ChunkBuildContext;"
                    + "Lorg/embeddedt/embeddium/impl/util/task/CancellationToken;)"
                    + "Lorg/embeddedt/embeddium/impl/render/chunk/compile/ChunkBuildOutput;",
            at = @At("HEAD"),
            remap = false,
            require = 1
    )
    private void wildernessOdysseyApi$beginWaterHandoffCompilation(
            CallbackInfoReturnable<Object> callbackInfo
    ) {
        WaterRenderCoordinator.beginSectionCompilation(wildernessOdysseyApi$waterSectionKey);
    }

    /** Attaches a complete-mask receipt to this exact build output. */
    @Inject(
            method = "execute("
                    + "Lorg/embeddedt/embeddium/impl/render/chunk/compile/ChunkBuildContext;"
                    + "Lorg/embeddedt/embeddium/impl/util/task/CancellationToken;)"
                    + "Lorg/embeddedt/embeddium/impl/render/chunk/compile/ChunkBuildOutput;",
            at = @At("RETURN"),
            remap = false,
            require = 1
    )
    private void wildernessOdysseyApi$finishWaterHandoffCompilation(
            CallbackInfoReturnable<Object> callbackInfo
    ) {
        WaterHandoffReceipt receipt = WaterRenderCoordinator.finishSectionCompilation(
                wildernessOdysseyApi$waterSectionKey);
        Object output = callbackInfo.getReturnValue();
        if (output instanceof WaterHandoffBuildOutput taggedOutput) {
            taggedOutput.wildernessOdysseyApi$setWaterHandoffReceipt(receipt);
        }
    }
}
