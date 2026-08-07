package com.thunder.wildernessodysseyapi.mixin;

import com.thunder.wildernessodysseyapi.watersystem.water.render.WaterHandoffBuildOutput;
import com.thunder.wildernessodysseyapi.watersystem.water.render.WaterHandoffReceipt;
import com.thunder.wildernessodysseyapi.watersystem.water.render.WaterRenderCoordinator;
import net.minecraft.client.renderer.chunk.SectionRenderDispatcher;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Acknowledges vanilla water suppression only after all section uploads finish.
 *
 * <p>NeoForge does not expose an event between the completion of vanilla's GPU
 * upload futures and {@code RenderSection#setCompiled}, so a narrow client
 * mixin owns that handoff. Minecraft 1.21.1 identifies a reusable render
 * section by its block-space origin; the receipt is acknowledged only when
 * that origin still resolves to the section that produced the uploaded
 * buffers.</p>
 */
@Mixin(SectionRenderDispatcher.RenderSection.class)
public abstract class VanillaSectionUploadHandoffMixin {

    /** Returns the current block-space origin of this reusable 1.21.1 section. */
    @Shadow
    public abstract BlockPos getOrigin();

    /** {@code setCompiled} runs after every layer upload future has completed. */
    @Inject(
            method = "setCompiled(Lnet/minecraft/client/renderer/chunk/SectionRenderDispatcher$CompiledSection;)V",
            at = @At("TAIL")
    )
    private void wildernessOdysseyApi$acknowledgeWaterHandoff(
            SectionRenderDispatcher.CompiledSection compiled,
            CallbackInfo callbackInfo
    ) {
        WaterHandoffReceipt receipt =
                ((WaterHandoffBuildOutput) (Object) compiled)
                        .wildernessOdysseyApi$getWaterHandoffReceipt();
        long currentSectionKey = SectionPos.of(getOrigin()).asLong();
        if (receipt.sectionKey() == currentSectionKey) {
            WaterRenderCoordinator.acknowledgeSectionUpload(receipt);
        }
    }
}
