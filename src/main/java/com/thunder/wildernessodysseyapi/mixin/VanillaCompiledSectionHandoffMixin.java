package com.thunder.wildernessodysseyapi.mixin;

import com.thunder.wildernessodysseyapi.watersystem.water.render.WaterHandoffBuildOutput;
import com.thunder.wildernessodysseyapi.watersystem.water.render.WaterHandoffReceipt;
import com.thunder.wildernessodysseyapi.watersystem.water.render.WaterRenderCoordinator;
import net.minecraft.client.renderer.chunk.SectionRenderDispatcher;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Carries a vanilla suppression receipt through asynchronous GPU upload. */
@Mixin(SectionRenderDispatcher.CompiledSection.class)
public class VanillaCompiledSectionHandoffMixin implements WaterHandoffBuildOutput {

    @Unique
    private WaterHandoffReceipt wildernessOdysseyApi$waterHandoffReceipt = WaterHandoffReceipt.NONE;

    /** Attaches the receipt staged by the immediately preceding section compile. */
    @Inject(method = "<init>", at = @At("RETURN"))
    private void wildernessOdysseyApi$attachWaterHandoffReceipt(CallbackInfo callbackInfo) {
        wildernessOdysseyApi$waterHandoffReceipt =
                WaterRenderCoordinator.takeCompletedCompilation();
    }

    @Override
    public void wildernessOdysseyApi$setWaterHandoffReceipt(WaterHandoffReceipt receipt) {
        wildernessOdysseyApi$waterHandoffReceipt = receipt == null
                ? WaterHandoffReceipt.NONE
                : receipt;
    }

    @Override
    public WaterHandoffReceipt wildernessOdysseyApi$getWaterHandoffReceipt() {
        return wildernessOdysseyApi$waterHandoffReceipt;
    }
}
