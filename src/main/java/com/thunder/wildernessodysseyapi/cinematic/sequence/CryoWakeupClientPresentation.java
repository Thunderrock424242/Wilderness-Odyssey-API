package com.thunder.wildernessodysseyapi.cinematic.sequence;

import com.thunder.wildernessodysseyapi.ai.voice.VoiceEmotion;
import com.thunder.wildernessodysseyapi.cinematic.client.AetherCinematicVoice;
import com.thunder.wildernessodysseyapi.cinematic.client.CinematicClientController;
import com.thunder.wildernessodysseyapi.cinematic.client.CinematicPostEffectController;
import com.thunder.wildernessodysseyapi.cinematic.client.ClientCinematicPresentation;
import net.minecraft.client.gui.Font;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.ViewportEvent;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/** Detached reveal, first-person recovery, subtitles, and local voice for the cryo awakening. */
public final class CryoWakeupClientPresentation implements ClientCinematicPresentation {
    private static final Component CONTAMINATION = Component.translatable(
            "cinematic.wildernessodysseyapi.cryo.contamination"
    );
    private static final Component REVIVAL_ACTIVE = Component.translatable(
            "cinematic.wildernessodysseyapi.cryo.revival_active"
    );
    private static final Component OBJECTIVE = Component.translatable(
            "cinematic.wildernessodysseyapi.cryo.objective"
    );
    private static final Component SPEAKER_LABEL = Component.translatable(
            "cinematic.wildernessodysseyapi.cryo.speaker"
    );
    private static final Component BOOT_PRIMARY = Component.translatable(
            "cinematic.wildernessodysseyapi.cryo.boot.primary"
    );
    private static final Component BOOT_SECONDARY = Component.translatable(
            "cinematic.wildernessodysseyapi.cryo.boot.secondary"
    );

    private static final Set<ResourceLocation> STAGES = Set.of(
            CryoWakeupSequence.BLACK_SCREEN,
            CryoWakeupSequence.EXTERIOR_REVEAL,
            CryoWakeupSequence.MEDICAL_DIAGNOSTIC,
            CryoWakeupSequence.REVIVAL_PROTOCOL,
            CryoWakeupSequence.CARDIAC_PACING,
            CryoWakeupSequence.SUSPENSION_DRAIN,
            CryoWakeupSequence.BLACKOUT_TRANSITION,
            CryoWakeupSequence.EYES_REOPENING,
            CryoWakeupSequence.MASK_RELEASE,
            CryoWakeupSequence.CRYO_OPENING,
            CryoWakeupSequence.BALANCE_CHECK,
            CryoWakeupSequence.RECOVERY_WALK
    );

    private static final Map<ResourceLocation, String> NARRATION_KEYS = Map.ofEntries(
            cue(CryoWakeupSequence.NARRATION_MEDICAL_ONLINE, "medical_online"),
            cue(CryoWakeupSequence.NARRATION_OCCUPANT_DETECTED, "occupant_detected"),
            cue(CryoWakeupSequence.NARRATION_CONTAMINATION, "contamination"),
            cue(CryoWakeupSequence.NARRATION_FILTRATION_OFFLINE, "filtration_offline"),
            cue(CryoWakeupSequence.NARRATION_REVIVAL_AUTHORIZED, "revival_authorized"),
            cue(CryoWakeupSequence.NARRATION_THERMAL_RESTORATION, "thermal_restoration"),
            cue(CryoWakeupSequence.NARRATION_CIRCULATORY_ASSIST, "circulatory_assist"),
            cue(CryoWakeupSequence.NARRATION_CRYOPROTECTANT_PURGE, "cryoprotectant_purge"),
            cue(CryoWakeupSequence.NARRATION_REANIMATION_COMPOUND, "reanimation_compound"),
            cue(CryoWakeupSequence.NARRATION_CARDIAC_LOW, "cardiac_low"),
            cue(CryoWakeupSequence.NARRATION_PACING, "pacing"),
            cue(CryoWakeupSequence.NARRATION_RHYTHM_RESTORED, "rhythm_restored"),
            cue(CryoWakeupSequence.NARRATION_RESPIRATORY_RESPONSE, "respiratory_response"),
            cue(CryoWakeupSequence.NARRATION_DRAINING, "draining"),
            cue(CryoWakeupSequence.NARRATION_MASK_RELEASING, "mask_releasing"),
            cue(CryoWakeupSequence.NARRATION_MOVE_SLOWLY, "move_slowly"),
            cue(CryoWakeupSequence.NARRATION_AWAKE, "awake"),
            cue(CryoWakeupSequence.NARRATION_AETHER_IDENTITY, "aether_identity"),
            cue(CryoWakeupSequence.NARRATION_AETHER_LIMITS, "aether_limits"),
            cue(CryoWakeupSequence.NARRATION_FIND_EXIT, "find_exit")
    );

    private final CinematicPostEffectController postEffects = new CinematicPostEffectController();

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
        if (stage == null || CryoWakeupSequence.RECOVERY_WALK.equals(stage)) {
            return;
        }
        float progress = state.stageProgress((float) event.getPartialTick());
        float time = gameTime((float) event.getPartialTick());
        if (CryoWakeupPresentationModel.isExteriorStage(stage)) {
            Vec3 camera = detachedCameraPosition(state, stage, progress);
            Vec3 target = exteriorFocus(state);
            Vec3 delta = target.subtract(camera);
            double horizontal = Math.sqrt(delta.x * delta.x + delta.z * delta.z);
            event.setYaw((float) Math.toDegrees(Math.atan2(-delta.x, delta.z)));
            event.setPitch((float) -Math.toDegrees(Math.atan2(delta.y, horizontal)));
            event.setRoll(CryoWakeupPresentationModel.rollOffset(stage, progress, time) * 0.28F);
            return;
        }

        event.setYaw(state.baseYaw() + CryoWakeupPresentationModel.yawOffset(stage, progress, time));
        event.setPitch(Mth.clamp(
                state.basePitch() + CryoWakeupPresentationModel.pitchOffset(stage, progress, time),
                -90.0F,
                90.0F
        ));
        event.setRoll(event.getRoll() + CryoWakeupPresentationModel.rollOffset(stage, progress, time));
    }

    @Override
    public Optional<Vec3> cameraPosition(CinematicClientController state, float partialTick) {
        ResourceLocation stage = state.stageId();
        if (stage == null || CryoWakeupSequence.RECOVERY_WALK.equals(stage)) {
            return Optional.empty();
        }
        float progress = state.stageProgress(partialTick);
        if (CryoWakeupPresentationModel.isExteriorStage(stage)) {
            return Optional.of(detachedCameraPosition(state, stage, progress));
        }
        if (CryoWakeupPresentationModel.isFirstPersonStage(stage)) {
            Vec3 forward = facingVector(state.baseYaw());
            return Optional.of(Vec3.atLowerCornerOf(state.anchor())
                    .add(0.5D, 1.02D, 0.5D)
                    .add(forward.scale(0.28D)));
        }
        return Optional.empty();
    }

    @Override
    public void applyFov(CinematicClientController state, ViewportEvent.ComputeFov event) {
        Minecraft minecraft = Minecraft.getInstance();
        float accessibility = minecraft.options.fovEffectScale().get().floatValue();
        ResourceLocation stage = state.stageId();
        if (stage == null || accessibility <= 0.01F) {
            return;
        }
        float multiplier;
        if (CryoWakeupPresentationModel.isExteriorStage(stage)) {
            multiplier = 0.76F;
        } else if (CryoWakeupPresentationModel.isFirstPersonStage(stage)) {
            multiplier = 0.90F;
        } else {
            multiplier = 1.0F;
        }
        event.setFOV(Mth.lerp(accessibility, event.getFOV(), event.getFOV() * multiplier));
    }

    @Override
    public boolean forcesFirstPerson(CinematicClientController state) {
        return !CryoWakeupSequence.RECOVERY_WALK.equals(state.stageId());
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
        float time = gameTime(partialTick);
        float blurStrength = CryoWakeupPresentationModel.blurStrength(stage, progress);
        postEffects.process(partialTick, blurStrength);
        if (blurStrength > 0.0F) {
            graphics.fill(0, 0, width, height, argb(blurStrength * 0.025F, 0xD8F2EE));
        }

        if (CryoWakeupSequence.BLACK_SCREEN.equals(stage)
                || CryoWakeupSequence.BLACKOUT_TRANSITION.equals(stage)) {
            graphics.fill(0, 0, width, height, 0xFF000000);
        }
        if (CryoWakeupSequence.BLACK_SCREEN.equals(stage)) {
            renderBootSequence(graphics, width, height, progress);
        }

        float suspensionAlpha = CryoWakeupPresentationModel.suspensionAlpha(stage, progress);
        if (suspensionAlpha > 0.0F) {
            graphics.fill(0, 0, width, height, argb(suspensionAlpha, 0x1B7C79));
            int scanY = Mth.floor((time * 1.8F) % Math.max(1, height));
            graphics.fill(0, scanY, width, Math.min(height, scanY + 2), 0x284ED5CE);
        }

        float warningAlpha = CryoWakeupPresentationModel.warningAlpha(stage, progress, time);
        if (warningAlpha > 0.0F) {
            graphics.fill(0, 0, width, height, argb(warningAlpha, 0xA10916));
        }

        float pacingFlash = CryoWakeupPresentationModel.pacingFlash(stage, progress);
        if (pacingFlash > 0.0F) {
            graphics.fill(0, 0, width, height, argb(pacingFlash * 0.34F, 0xE6FFFF));
        }

        if (CryoWakeupPresentationModel.isFirstPersonStage(stage)) {
            renderCondensation(graphics, width, height, stage, time);
            float eyeOpen = CryoWakeupPresentationModel.eyeOpenAmount(stage, progress);
            int lidHeight = Mth.clamp(Math.round((1.0F - eyeOpen) * height * 0.5F), 0, (height + 1) / 2);
            if (lidHeight > 0) {
                graphics.fill(0, 0, width, lidHeight, 0xFF000000);
                graphics.fill(0, height - lidHeight, width, height, 0xFF000000);
            }
        }

        if (CryoWakeupPresentationModel.showsMedicalTelemetry(stage)) {
            renderMedicalTelemetry(graphics, width, height, stage, progress, time, warningAlpha);
        }

        Component status = statusMessage(stage);
        if (status != null && !CryoWakeupSequence.BLACKOUT_TRANSITION.equals(stage)) {
            int statusColor = warningAlpha > 0.04F ? 0xFFFF6A72 : 0xFFB9FFF9;
            graphics.drawCenteredString(
                    Minecraft.getInstance().font,
                    status,
                    width / 2,
                    Math.max(16, Math.round(height * 0.12F)),
                    statusColor
            );
        }
        renderSubtitle(state, graphics, width, height, partialTick);
    }

    @Override
    public Optional<Component> narration(ResourceLocation cueId) {
        String key = NARRATION_KEYS.get(cueId);
        return key == null
                ? Optional.empty()
                : Optional.of(Component.translatable("cinematic.wildernessodysseyapi.cryo.narration." + key));
    }

    @Override
    public void onNarration(CinematicClientController state, ResourceLocation cueId, Component text) {
        AetherCinematicVoice.speak(text, narrationEmotion(cueId), narrationRadioEffect(cueId));
    }

    @Override
    public void onStopped(CinematicClientController state) {
        AetherCinematicVoice.stop();
    }

    @Override
    public Component completionMessage() {
        return OBJECTIVE;
    }

    @Override
    public int completionMessageTicks() {
        return 160;
    }

    /** Used by the cryo renderer to show the presentation-only local-player proxy. */
    public static boolean shouldRenderOccupant(ResourceLocation stage) {
        return CryoWakeupPresentationModel.isExteriorStage(stage);
    }

    /** Resolves only registered authored cues to bounded local voice delivery moods. */
    static VoiceEmotion narrationEmotion(ResourceLocation cueId) {
        if (CryoWakeupSequence.NARRATION_CONTAMINATION.equals(cueId)
                || CryoWakeupSequence.NARRATION_FILTRATION_OFFLINE.equals(cueId)
                || CryoWakeupSequence.NARRATION_CARDIAC_LOW.equals(cueId)) {
            return VoiceEmotion.CONCERNED;
        }
        if (CryoWakeupSequence.NARRATION_REVIVAL_AUTHORIZED.equals(cueId)
                || CryoWakeupSequence.NARRATION_PACING.equals(cueId)) {
            return VoiceEmotion.CONCERNED;
        }
        if (CryoWakeupSequence.NARRATION_AETHER_IDENTITY.equals(cueId)
                || CryoWakeupSequence.NARRATION_AETHER_LIMITS.equals(cueId)) {
            return VoiceEmotion.DAMAGED;
        }
        return VoiceEmotion.CALM;
    }

    /** Adds restrained corruption only to A.E.T.H.E.R's damaged self-disclosure. */
    static float narrationRadioEffect(ResourceLocation cueId) {
        if (CryoWakeupSequence.NARRATION_AETHER_IDENTITY.equals(cueId)) {
            return 0.08F;
        }
        if (CryoWakeupSequence.NARRATION_AETHER_LIMITS.equals(cueId)) {
            return 0.06F;
        }
        return 0.015F;
    }

    private static void renderSubtitle(
            CinematicClientController state,
            GuiGraphics graphics,
            int width,
            int height,
            float partialTick
    ) {
        Component subtitle = state.subtitle();
        float alpha = state.subtitleAlpha(partialTick);
        if (subtitle == null || alpha <= 0.0F) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        int maxWidth = Math.max(80, Math.min(width - 40, 520));
        List<FormattedCharSequence> lines = minecraft.font.split(subtitle, maxWidth);
        int firstY = Math.round(height * 0.78F) - Math.max(0, lines.size() - 1) * 5;
        int backgroundAlpha = Mth.clamp(Math.round(alpha * 150.0F), 0, 150);
        int textAlpha = Mth.clamp(Math.round(alpha * 255.0F), 0, 255);
        int labelY = firstY - 13;
        int labelWidth = minecraft.font.width(SPEAKER_LABEL);
        graphics.fill(
                width / 2 - labelWidth / 2 - 4,
                labelY - 2,
                width / 2 + labelWidth / 2 + 4,
                labelY + 10,
                (backgroundAlpha << 24) | 0x071012
        );
        graphics.drawCenteredString(
                minecraft.font,
                SPEAKER_LABEL,
                width / 2,
                labelY,
                (textAlpha << 24) | 0x74E8E1
        );
        for (int i = 0; i < lines.size(); i++) {
            FormattedCharSequence line = lines.get(i);
            int lineWidth = minecraft.font.width(line);
            int y = firstY + i * 11;
            graphics.fill(
                    width / 2 - lineWidth / 2 - 4,
                    y - 2,
                    width / 2 + lineWidth / 2 + 4,
                    y + 10,
                    (backgroundAlpha << 24) | 0x071012
            );
            graphics.drawCenteredString(minecraft.font, line, width / 2, y, (textAlpha << 24) | 0xDFFFFA);
        }
    }

    private static void renderBootSequence(
            GuiGraphics graphics,
            int width,
            int height,
            float progress
    ) {
        Font font = Minecraft.getInstance().font;
        int centerY = height / 2 - 10;
        int alpha = Mth.clamp(Math.round(smooth(progress) * 255.0F), 0, 255);
        graphics.drawCenteredString(font, BOOT_PRIMARY, width / 2, centerY, (alpha << 24) | 0x8FFCF3);
        if (progress >= 0.30F) {
            graphics.drawCenteredString(
                    font,
                    BOOT_SECONDARY,
                    width / 2,
                    centerY + 14,
                    (alpha << 24) | 0xD6FFFB
            );
        }
        int barWidth = Math.min(180, Math.max(80, width / 3));
        int barX = width / 2 - barWidth / 2;
        int barY = centerY + 32;
        graphics.fill(barX, barY, barX + barWidth, barY + 2, 0x553B6F6C);
        graphics.fill(barX, barY, barX + Math.round(barWidth * progress), barY + 2, 0xCC72E4DC);
    }

    private static void renderMedicalTelemetry(
            GuiGraphics graphics,
            int width,
            int height,
            ResourceLocation stage,
            float progress,
            float time,
            float warningAlpha
    ) {
        if (width < 260 || height < 150) {
            return;
        }
        Font font = Minecraft.getInstance().font;
        int panelWidth = Math.min(176, Math.max(152, width / 3));
        int panelHeight = 94;
        int x = 12;
        int y = Math.max(12, height / 12);
        int accent = warningAlpha > 0.04F ? 0xFFE8646C : 0xFF72E4DC;

        graphics.fill(x, y, x + panelWidth, y + panelHeight, 0xA6071113);
        graphics.hLine(x, x + panelWidth, y, accent);
        graphics.hLine(x, x + panelWidth, y + panelHeight, 0x803B6F6C);
        graphics.vLine(x, y, y + panelHeight, 0x803B6F6C);
        graphics.vLine(x + panelWidth, y, y + panelHeight, 0x803B6F6C);

        graphics.drawString(
                font,
                Component.translatable("cinematic.wildernessodysseyapi.cryo.telemetry.title"),
                x + 6,
                y + 5,
                accent,
                false
        );
        graphics.drawString(font, telemetryStatus(stage), x + 6, y + 17, 0xFFC8DDD9, false);

        String temperature = String.format(
                Locale.ROOT,
                "%.1f",
                CryoWakeupPresentationModel.coreTemperatureCelsius(stage, progress)
        );
        int heartRate = CryoWakeupPresentationModel.heartRateBpm(stage, progress);
        int oxygen = CryoWakeupPresentationModel.oxygenSaturation(stage, progress);
        graphics.drawString(
                font,
                Component.translatable("cinematic.wildernessodysseyapi.cryo.telemetry.temperature", temperature),
                x + 6,
                y + 31,
                0xFFE8F8F5,
                false
        );
        graphics.drawString(
                font,
                Component.translatable("cinematic.wildernessodysseyapi.cryo.telemetry.heart_rate", heartRate),
                x + 6,
                y + 42,
                0xFFE8F8F5,
                false
        );
        graphics.drawString(
                font,
                Component.translatable("cinematic.wildernessodysseyapi.cryo.telemetry.oxygen", oxygen),
                x + 6,
                y + 53,
                oxygen < 90 ? 0xFFFF8B91 : 0xFF9EF6CF,
                false
        );

        renderWaveform(graphics, x + 6, y + 70, panelWidth - 12, heartRate, time, accent);
        float thermalProgress = Mth.clamp(
                (CryoWakeupPresentationModel.coreTemperatureCelsius(stage, progress) - 8.2F) / 28.4F,
                0.0F,
                1.0F
        );
        graphics.fill(x + 6, y + 87, x + panelWidth - 6, y + 89, 0x553B6F6C);
        graphics.fill(
                x + 6,
                y + 87,
                x + 6 + Math.round((panelWidth - 12) * thermalProgress),
                y + 89,
                accent
        );
    }

    private static void renderWaveform(
            GuiGraphics graphics,
            int x,
            int y,
            int width,
            int heartRate,
            float time,
            int color
    ) {
        int period = Mth.clamp(54 - heartRate / 2, 16, 44);
        int offset = Mth.floor(time * 5.5F);
        int previousY = y;
        for (int i = 1; i < width; i += 2) {
            int phase = Math.floorMod(i + offset, period);
            int currentY = y;
            if (phase == 0 || phase == 1) {
                currentY = y - 7;
            } else if (phase == 2 || phase == 3) {
                currentY = y + 4;
            } else if (phase == 6 || phase == 7) {
                currentY = y - 2;
            }
            graphics.fill(
                    x + i - 1,
                    Math.min(previousY, currentY),
                    x + i + 1,
                    Math.max(previousY, currentY) + 1,
                    color
            );
            previousY = currentY;
        }
    }

    private static void renderCondensation(
            GuiGraphics graphics,
            int width,
            int height,
            ResourceLocation stage,
            float time
    ) {
        float fogAlpha = CryoWakeupPresentationModel.breathFogAlpha(stage, time);
        if (fogAlpha > 0.0F) {
            graphics.fill(0, Math.round(height * 0.73F), width, height, argb(fogAlpha, 0xD8F2EE));
        }
        for (int i = 0; i < 10; i++) {
            boolean left = (i & 1) == 0;
            int edgeOffset = 8 + (i * 17) % Math.max(12, Math.min(54, width / 5));
            int dropX = left ? edgeOffset : width - edgeOffset;
            int dropY = Math.floorMod(Mth.floor(time * (0.42F + i * 0.035F)) + i * 37, Math.max(1, height));
            int dropLength = 4 + i % 5;
            graphics.fill(dropX, dropY, dropX + 1, Math.min(height, dropY + dropLength), 0x387EE0D8);
        }
    }

    private static Component telemetryStatus(ResourceLocation stage) {
        String key;
        if (CryoWakeupSequence.EXTERIOR_REVEAL.equals(stage)) {
            key = "cryostasis";
        } else if (CryoWakeupSequence.MEDICAL_DIAGNOSTIC.equals(stage)) {
            key = "diagnostic";
        } else if (CryoWakeupSequence.REVIVAL_PROTOCOL.equals(stage)) {
            key = "rewarming";
        } else if (CryoWakeupSequence.CARDIAC_PACING.equals(stage)) {
            key = "pacing";
        } else if (CryoWakeupSequence.SUSPENSION_DRAIN.equals(stage)) {
            key = "circulation";
        } else if (CryoWakeupSequence.EYES_REOPENING.equals(stage)) {
            key = "neurological";
        } else if (CryoWakeupSequence.MASK_RELEASE.equals(stage)) {
            key = "respiration";
        } else {
            key = "vestibular";
        }
        return Component.translatable("cinematic.wildernessodysseyapi.cryo.telemetry.status." + key);
    }

    private static Vec3 detachedCameraPosition(
            CinematicClientController state,
            ResourceLocation stage,
            float progress
    ) {
        Vec3 forward = facingVector(state.baseYaw());
        Vec3 right = new Vec3(forward.z, 0.0D, -forward.x);
        double orbit = CryoWakeupPresentationModel.exteriorOrbitOffset(stage, progress);
        double distance = CryoWakeupSequence.EXTERIOR_REVEAL.equals(stage)
                ? Mth.lerp(smooth(progress), 3.05D, 2.35D)
                : 2.35D;
        double vertical = CryoWakeupSequence.SUSPENSION_DRAIN.equals(stage)
                ? Mth.lerp(smooth(progress), 0.48D, 0.22D)
                : 0.48D;
        return exteriorFocus(state)
                .add(forward.scale(distance))
                .add(right.scale(orbit))
                .add(0.0D, vertical, 0.0D);
    }

    private static Vec3 exteriorFocus(CinematicClientController state) {
        return Vec3.atLowerCornerOf(state.anchor()).add(0.5D, 0.98D, 0.5D);
    }

    private static Vec3 facingVector(float yaw) {
        double radians = Math.toRadians(yaw);
        return new Vec3(-Math.sin(radians), 0.0D, Math.cos(radians));
    }

    private static Component statusMessage(ResourceLocation stage) {
        if (CryoWakeupSequence.MEDICAL_DIAGNOSTIC.equals(stage)
                || CryoWakeupSequence.REVIVAL_PROTOCOL.equals(stage)
                || CryoWakeupSequence.CARDIAC_PACING.equals(stage)
                || CryoWakeupSequence.SUSPENSION_DRAIN.equals(stage)) {
            return CryoWakeupSequence.MEDICAL_DIAGNOSTIC.equals(stage) ? CONTAMINATION : REVIVAL_ACTIVE;
        }
        return null;
    }

    private static Map.Entry<ResourceLocation, String> cue(ResourceLocation id, String key) {
        return Map.entry(id, key);
    }

    private static float gameTime(float partialTick) {
        Minecraft minecraft = Minecraft.getInstance();
        return (minecraft.level == null ? 0.0F : minecraft.level.getGameTime()) + partialTick;
    }

    private static float smooth(float value) {
        float t = Mth.clamp(value, 0.0F, 1.0F);
        return t * t * (3.0F - 2.0F * t);
    }

    private static int argb(float alpha, int rgb) {
        int a = Mth.clamp(Math.round(alpha * 255.0F), 0, 255);
        return (a << 24) | (rgb & 0xFFFFFF);
    }
}
