package com.thunder.wildernessodysseyapi.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import com.thunder.wildernessodysseyapi.watersystem.water.entity.BoatTiltStore;
import com.thunder.wildernessodysseyapi.watersystem.water.entity.BoatWaveRenderState;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.AbstractBoatRenderer;
import net.minecraft.client.renderer.entity.state.BoatRenderState;
import net.minecraft.world.entity.vehicle.AbstractBoat;
import org.joml.Quaternionf;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Applies the shared wave surface response to Minecraft's boat renderer.
 *
 * <p>Minecraft 1.21 renders boats through {@link BoatRenderState}, so one
 * injection copies the entity-keyed response into that state and a second
 * injection applies it after vanilla rotates the pose into boat-local space.
 * A mixin is necessary because NeoForge does not expose an event between the
 * boat's yaw transform and its model draw.</p>
 */
@Mixin(AbstractBoatRenderer.class)
public class BoatRenderMixin {

    @Inject(
            method = "extractRenderState(Lnet/minecraft/world/entity/vehicle/AbstractBoat;"
                    + "Lnet/minecraft/client/renderer/entity/state/BoatRenderState;F)V",
            at = @At("TAIL")
    )
    private void copyWaveResponse(AbstractBoat boat, BoatRenderState renderState,
                                  float partialTick, CallbackInfo callbackInfo) {
        float[] response = BoatTiltStore.get(boat.getId());
        BoatWaveRenderState waveState = (BoatWaveRenderState) renderState;
        waveState.wildernessodysseyapi$setWaveResponse(response[0], response[1], response[2]);
    }

    @Inject(
            method = "render(Lnet/minecraft/client/renderer/entity/state/BoatRenderState;"
                    + "Lcom/mojang/blaze3d/vertex/PoseStack;"
                    + "Lnet/minecraft/client/renderer/MultiBufferSource;I)V",
            at = @At(
                    value = "INVOKE",
                    target = "Lcom/mojang/blaze3d/vertex/PoseStack;mulPose(Lorg/joml/Quaternionfc;)V",
                    ordinal = 0,
                    shift = At.Shift.AFTER
            )
    )
    private void applyWaveResponse(BoatRenderState renderState, PoseStack poseStack,
                                   MultiBufferSource bufferSource, int packedLight,
                                   CallbackInfo callbackInfo) {
        BoatWaveRenderState waveState = (BoatWaveRenderState) renderState;
        float pitch = waveState.wildernessodysseyapi$getWavePitch();
        float roll = waveState.wildernessodysseyapi$getWaveRoll();
        float bob = waveState.wildernessodysseyapi$getWaveBob();

        if (Math.abs(pitch) < 0.1f && Math.abs(roll) < 0.1f && Math.abs(bob) < 0.001f) {
            return;
        }

        // Bob is a render transform so it cannot fight server boat position.
        // Pitch and roll are applied after yaw, in the boat's local axes.
        poseStack.translate(0.0, bob, 0.0);
        poseStack.mulPose(new Quaternionf()
                .rotateZ((float) Math.toRadians(roll))
                .rotateX((float) Math.toRadians(pitch)));
    }
}
