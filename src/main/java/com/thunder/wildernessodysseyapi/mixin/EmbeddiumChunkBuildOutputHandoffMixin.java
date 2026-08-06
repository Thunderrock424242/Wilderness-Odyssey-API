package com.thunder.wildernessodysseyapi.mixin;

import com.thunder.wildernessodysseyapi.watersystem.water.render.WaterHandoffBuildOutput;
import com.thunder.wildernessodysseyapi.watersystem.water.render.WaterHandoffReceipt;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Unique;

/** Carries one exact suppression generation through Embeddium's build queue. */
@Pseudo
@Mixin(
        targets = "org.embeddedt.embeddium.impl.render.chunk.compile.ChunkBuildOutput",
        remap = false
)
public class EmbeddiumChunkBuildOutputHandoffMixin implements WaterHandoffBuildOutput {

    @Unique
    private WaterHandoffReceipt wildernessOdysseyApi$waterHandoffReceipt = WaterHandoffReceipt.NONE;

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
