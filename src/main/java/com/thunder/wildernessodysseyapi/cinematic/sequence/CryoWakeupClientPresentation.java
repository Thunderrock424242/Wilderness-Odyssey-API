package com.thunder.wildernessodysseyapi.cinematic.sequence;

import com.thunder.wildernessodysseyapi.cinematic.client.CinematicClientController;
import com.thunder.wildernessodysseyapi.cinematic.client.ClientCinematicPresentation;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.neoforged.neoforge.client.event.ViewportEvent;

import java.util.Set;

/** Client camera, eyelid, warning, and objective presentation for cryo wake-up. */
public final class CryoWakeupClientPresentation implements ClientCinematicPresentation {
    private static final Component SYSTEM_FAILURE = Component.translatable(
            "cinematic.wildernessodysseyapi.cryo.system_failure"
    );
    private static final Component RELEASE_INITIATED = Component.translatable(
            "cinematic.wildernessodysseyapi.cryo.release_initiated"
    );
    private static final Component OBJECTIVE = Component.translatable(
            "cinematic.wildernessodysseyapi.cryo.objective"
    );
    private static final Set<ResourceLocation> STAGES = Set.of(
            CryoWakeupSequence.BLACK_SCREEN,
            CryoWakeupSequence.MACHINERY_HUM,
            CryoWakeupSequence.HEARTBEAT,
            CryoWakeupSequence.EYES_PARTIAL,
            CryoWakeupSequence.EYES_CLOSED,
            CryoWakeupSequence.EYES_REOPENING,
            CryoWakeupSequence.LIGHTS_FLICKER,
            CryoWakeupSequence.WARNING_STARTED,
            CryoWakeupSequence.WARNING_LIGHTS,
            CryoWakeupSequence.ALARM_BEEPS,
            CryoWakeupSequence.RELEASE_STARTED,
            CryoWakeupSequence.LOCKS_DISENGAGED,
            CryoWakeupSequence.MIST_RELEASE,
            CryoWakeupSequence.CRYO_OPENING,
            CryoWakeupSequence.CAMERA_TURN,
            CryoWakeupSequence.CRYO_OPEN,
            CryoWakeupSequence.LIGHTS_STABLE,
            CryoWakeupSequence.CAMERA_RELEASE,
            CryoWakeupSequence.CONTROL_RETURN
    );

    @Override
    public ResourceLocation sequenceId() {
        return CryoWakeupSequence.ID;
    }

    @Override
    public boolean recognizesStage(ResourceLocation stageId) {
        return STAGES.contains(stageId);
    }

    @Override
    public void applyCamera(CinematicClientController state, ViewportEvent.ComputeCameraAngles event) {
        ResourceLocation stage = state.stageId();
        if (stage == null || stage.equals(CryoWakeupSequence.CONTROL_RETURN)) {
            return;
        }
        float progress = state.stageProgress((float) event.getPartialTick());
        Minecraft minecraft = Minecraft.getInstance();
        float time = (minecraft.level == null ? 0.0F : minecraft.level.getGameTime())
                + (float) event.getPartialTick();

        float yaw = state.baseYaw() + CryoWakeupPresentationModel.yawOffset(stage, progress, time);
        float pitch = state.basePitch() + CryoWakeupPresentationModel.pitchOffset(stage, progress, time);
        float roll = event.getRoll() + CryoWakeupPresentationModel.rollOffset(stage, progress, time);
        if (stage.equals(CryoWakeupSequence.CAMERA_RELEASE)) {
            float release = smooth(progress);
            yaw = Mth.rotLerp(release, yaw, event.getYaw());
            pitch = Mth.lerp(release, pitch, event.getPitch());
            roll = Mth.lerp(release, roll, event.getRoll());
        }

        event.setYaw(yaw);
        event.setPitch(Mth.clamp(pitch, -90.0F, 90.0F));
        event.setRoll(roll);
    }

    @Override
    public void renderOverlay(
            CinematicClientController state,
            GuiGraphics graphics,
            int width,
            int height,
            float partialTick
    ) {
        ResourceLocation stage = state.stageId();
        if (stage == null) {
            return;
        }
        float progress = state.stageProgress(partialTick);
        Minecraft minecraft = Minecraft.getInstance();
        float time = (minecraft.level == null ? 0.0F : minecraft.level.getGameTime()) + partialTick;

        float redAlpha = CryoWakeupPresentationModel.warningAlpha(stage, progress, time);
        if (redAlpha > 0.0F) {
            graphics.fill(0, 0, width, height, argb(redAlpha, 0x9E0710));
        }

        float eyeOpen = CryoWakeupPresentationModel.eyeOpenAmount(stage, progress);
        int lidHeight = Mth.clamp(Math.round((1.0F - eyeOpen) * height * 0.5F), 0, (height + 1) / 2);
        if (lidHeight > 0) {
            graphics.fill(0, 0, width, lidHeight, 0xFF000000);
            graphics.fill(0, height - lidHeight, width, height, 0xFF000000);
        }

        Component message = warningMessage(stage);
        if (message != null && eyeOpen > 0.35F) {
            int color = stage.equals(CryoWakeupSequence.WARNING_STARTED)
                    || stage.equals(CryoWakeupSequence.WARNING_LIGHTS)
                    || stage.equals(CryoWakeupSequence.ALARM_BEEPS)
                    ? 0xFFFF5555
                    : 0xFFE6F3F7;
            graphics.drawCenteredString(minecraft.font, message, width / 2, Math.round(height * 0.70F), color);
        }
    }

    @Override
    public Component completionMessage() {
        return OBJECTIVE;
    }

    @Override
    public int completionMessageTicks() {
        return 120;
    }

    private static Component warningMessage(ResourceLocation stage) {
        if (stage.equals(CryoWakeupSequence.WARNING_STARTED)
                || stage.equals(CryoWakeupSequence.WARNING_LIGHTS)
                || stage.equals(CryoWakeupSequence.ALARM_BEEPS)) {
            return SYSTEM_FAILURE;
        }
        if (stage.equals(CryoWakeupSequence.RELEASE_STARTED)
                || stage.equals(CryoWakeupSequence.LOCKS_DISENGAGED)
                || stage.equals(CryoWakeupSequence.MIST_RELEASE)
                || stage.equals(CryoWakeupSequence.CRYO_OPENING)) {
            return RELEASE_INITIATED;
        }
        return null;
    }

    private static int argb(float alpha, int rgb) {
        int a = Mth.clamp(Math.round(alpha * 255.0F), 0, 255);
        return (a << 24) | (rgb & 0xFFFFFF);
    }

    private static float smooth(float value) {
        float t = Mth.clamp(value, 0.0F, 1.0F);
        return t * t * (3.0F - 2.0F * t);
    }
}
