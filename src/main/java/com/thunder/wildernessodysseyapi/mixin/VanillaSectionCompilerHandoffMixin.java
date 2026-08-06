package com.thunder.wildernessodysseyapi.mixin;

import com.mojang.blaze3d.vertex.VertexSorting;
import com.thunder.wildernessodysseyapi.watersystem.water.render.WaterHandoffReceipt;
import com.thunder.wildernessodysseyapi.watersystem.water.render.WaterRenderCoordinator;
import net.minecraft.client.renderer.SectionBufferBuilderPack;
import net.minecraft.client.renderer.chunk.RenderChunkRegion;
import net.minecraft.client.renderer.chunk.SectionCompiler;
import net.minecraft.core.SectionPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;

/**
 * Records the exact fallback-top mask seen by one vanilla section compilation.
 *
 * <p>The result is staged on the compiler thread and attached to the immediately
 * created {@code CompiledSection}; this avoids acknowledging stale or cancelled
 * section work.</p>
 */
@Mixin(SectionCompiler.class)
public abstract class VanillaSectionCompilerHandoffMixin {

    /** Resets observations before vanilla visits the section's fluid cells. */
    @Inject(
            method = "compile("
                    + "Lnet/minecraft/core/SectionPos;"
                    + "Lnet/minecraft/client/renderer/chunk/RenderChunkRegion;"
                    + "Lcom/mojang/blaze3d/vertex/VertexSorting;"
                    + "Lnet/minecraft/client/renderer/SectionBufferBuilderPack;"
                    + "Ljava/util/List;)"
                    + "Lnet/minecraft/client/renderer/chunk/SectionCompiler$Results;",
            at = @At("HEAD")
    )
    private void wildernessOdysseyApi$beginWaterHandoffCompilation(
            SectionPos sectionPos,
            RenderChunkRegion region,
            VertexSorting sorting,
            SectionBufferBuilderPack buffers,
            List<?> additionalRenderers,
            CallbackInfoReturnable<SectionCompiler.Results> callbackInfo
    ) {
        WaterRenderCoordinator.beginSectionCompilation(sectionPos.asLong());
    }

    /** Stages a receipt only when every expected owned top was suppressed. */
    @Inject(
            method = "compile("
                    + "Lnet/minecraft/core/SectionPos;"
                    + "Lnet/minecraft/client/renderer/chunk/RenderChunkRegion;"
                    + "Lcom/mojang/blaze3d/vertex/VertexSorting;"
                    + "Lnet/minecraft/client/renderer/SectionBufferBuilderPack;"
                    + "Ljava/util/List;)"
                    + "Lnet/minecraft/client/renderer/chunk/SectionCompiler$Results;",
            at = @At("RETURN")
    )
    private void wildernessOdysseyApi$finishWaterHandoffCompilation(
            SectionPos sectionPos,
            RenderChunkRegion region,
            VertexSorting sorting,
            SectionBufferBuilderPack buffers,
            List<?> additionalRenderers,
            CallbackInfoReturnable<SectionCompiler.Results> callbackInfo
    ) {
        WaterHandoffReceipt receipt = WaterRenderCoordinator.finishSectionCompilation(
                sectionPos.asLong());
        WaterRenderCoordinator.stageCompletedCompilation(receipt);
    }
}
