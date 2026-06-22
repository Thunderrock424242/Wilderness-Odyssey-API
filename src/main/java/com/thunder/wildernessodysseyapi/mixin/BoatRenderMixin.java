package com.thunder.wildernessodysseyapi.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import com.thunder.wildernessodysseyapi.watersystem.water.entity.BoatTiltStore;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.BoatRenderer;
import net.minecraft.world.entity.vehicle.Boat;
import org.joml.Quaternionf;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Applies the shared wave surface response to the 1.21.1 boat renderer.
 *
 * <p>The injection runs after vanilla applies boat yaw, which lets pitch and
 * roll use boat-local axes. A mixin is necessary because NeoForge does not
 * expose an event between that yaw transform and the boat model draw.</p>
 */
@Mixin(BoatRenderer.class)
public class BoatRenderMixin {

    @Inject(
            method = "render(Lnet/minecraft/world/entity/vehicle/Boat;FF"
                    + "Lcom/mojang/blaze3d/vertex/PoseStack;"
                    + "Lnet/minecraft/client/renderer/MultiBufferSource;I)V",
            at = @At(
                    value = "INVOKE",
                    target = "Lcom/mojang/blaze3d/vertex/PoseStack;mulPose(Lorg/joml/Quaternionf;)V",
                    ordinal = 0,
                    shift = At.Shift.AFTER
            )
    )
    private void applyWaveResponse(Boat boat, float entityYaw, float partialTick,
                                   PoseStack poseStack, MultiBufferSource bufferSource,
                                   int packedLight, CallbackInfo callbackInfo) {
        float[] response = BoatTiltStore.get(boat.getId());
        float pitch = response[0];
        float roll = response[1];
        float bob = response[2];

        if (Math.abs(pitch) < 0.1f && Math.abs(roll) < 0.1f && Math.abs(bob) < 0.001f) {
            return;
        }

        // Bob remains render-only, while pitch and roll follow the local hull.
        poseStack.translate(0.0, bob, 0.0);
        poseStack.mulPose(new Quaternionf()
                .rotateZ((float) Math.toRadians(roll))
                .rotateX((float) Math.toRadians(pitch)));
    }
}
