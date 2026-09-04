package com.thunder.wildernessodysseyapi.mixin;

import com.thunder.wildernessodysseyapi.watersystem.water.render.WaterHandoffBuildOutput;
import com.thunder.wildernessodysseyapi.watersystem.water.render.WaterHandoffReceipt;
import com.thunder.wildernessodysseyapi.watersystem.water.render.WaterRenderCoordinator;
import com.thunder.wildernessodysseyapi.watersystem.water.render.WaterRendererSectionCoordinates;
import net.minecraft.core.SectionPos;
import org.joml.Vector3dc;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Tags the exact Sodium 0.8.12 meshing result that removed fallback tops. */
@Pseudo
@Mixin(
        targets = "net.caffeinemc.mods.sodium.client.render.chunk.compile.tasks.ChunkBuilderMeshingTask",
        remap = false
)
public class SodiumChunkMeshingHandoffMixin {

    @Unique
    private long wildernessOdysseyApi$waterSectionKey = Long.MIN_VALUE;

    /** Captures section coordinates without a compile-time Sodium dependency. */
    @Inject(method = "<init>", at = @At("RETURN"), remap = false, require = 1)
    private void wildernessOdysseyApi$captureWaterSection(
            @Coerce Object renderSection,
            int submitTime,
            Vector3dc cameraPosition,
            @Coerce Object renderContext,
            @Coerce Object sortBehavior,
            boolean forceSort,
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
                    + "Lnet/caffeinemc/mods/sodium/client/render/chunk/compile/ChunkBuildContext;"
                    + "Lnet/caffeinemc/mods/sodium/client/util/task/CancellationToken;)"
                    + "Lnet/caffeinemc/mods/sodium/client/render/chunk/compile/ChunkBuildOutput;",
            at = @At("HEAD"),
            remap = false,
            require = 1
    )
    private void wildernessOdysseyApi$beginWaterHandoffCompilation(
            CallbackInfoReturnable<Object> callbackInfo
    ) {
        WaterRenderCoordinator.beginSectionCompilation(wildernessOdysseyApi$waterSectionKey);
    }

    /** Attaches the current ownership receipt to this exact completed build output. */
    @Inject(
            method = "execute("
                    + "Lnet/caffeinemc/mods/sodium/client/render/chunk/compile/ChunkBuildContext;"
                    + "Lnet/caffeinemc/mods/sodium/client/util/task/CancellationToken;)"
                    + "Lnet/caffeinemc/mods/sodium/client/render/chunk/compile/ChunkBuildOutput;",
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
