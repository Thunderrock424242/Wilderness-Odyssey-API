package com.thunder.wildernessodysseyapi.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import com.thunder.wildernessodysseyapi.watersystem.water.entity.BoatTiltStore;
import net.minecraft.client.renderer.entity.BoatRenderer;
import net.minecraft.world.entity.vehicle.Boat;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * BoatRenderMixin
 *
 * Injects into BoatRenderer#render to apply the wave-driven
 * pitch and roll angles stored in BoatTiltStore.
 *
 * The rotation is applied to the PoseStack before the model is drawn,
 * so the entire boat (hull + passengers) tilts together.
 *
 * Rotation order: first roll (Z axis), then pitch (X axis).
 * This matches how a real boat responds to wave slope.
 */
@Mixin(BoatRenderer.class)
public class BoatRenderMixin {

    @Inject(
        method = "render",
        at = @At(value = "INVOKE",
                 target = "Lcom/mojang/blaze3d/vertex/PoseStack;pushPose()V",
                 shift = At.Shift.AFTER),
        require = 0   // don't crash if the method signature changes
    )
    private void applyWaveTilt(Boat boat, float entityYaw, float partialTick,
                                PoseStack poseStack,
                                net.minecraft.client.renderer.MultiBufferSource bufferSource,
                                int packedLight,
                                CallbackInfo ci) {
        float[] tilt = BoatTiltStore.get(boat.getId());
        float pitch = tilt[0];
        float roll  = tilt[1];

        if (Math.abs(pitch) < 0.1f && Math.abs(roll) < 0.1f) return;

        // Move pivot to boat centre, rotate, move back
        poseStack.translate(0.0, 0.375, 0.0);                 // boat model Y centre
        poseStack.mulPose(new org.joml.Quaternionf()
            .rotateZ((float) Math.toRadians(roll))
            .rotateX((float) Math.toRadians(pitch)));
        poseStack.translate(0.0, -0.375, 0.0);
    }
}
