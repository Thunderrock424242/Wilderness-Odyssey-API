package com.thunder.wildernessodysseyapi.mixin;

import com.thunder.wildernessodysseyapi.watersystem.water.render.WaterHandoffBuildOutput;
import com.thunder.wildernessodysseyapi.watersystem.water.render.WaterHandoffReceipt;
import com.thunder.wildernessodysseyapi.watersystem.water.render.WaterRenderCoordinator;
import net.minecraft.client.renderer.chunk.SectionRenderDispatcher;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Acknowledges vanilla water suppression only after all section uploads finish. */
@Mixin(SectionRenderDispatcher.RenderSection.class)
public abstract class VanillaSectionUploadHandoffMixin {

    @Shadow
    public abstract long getSectionNode();

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
        if (receipt.sectionKey() == getSectionNode()) {
            WaterRenderCoordinator.acknowledgeSectionUpload(receipt);
        }
    }
}
