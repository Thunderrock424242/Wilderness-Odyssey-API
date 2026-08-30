package com.thunder.wildernessodysseyapi.cinematic.sequence;

import com.thunder.wildernessodysseyapi.cinematic.client.CinematicClientController;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;

import java.util.List;
import java.util.Locale;

/**
 * Draws the deliberately sparse cryo medical HUD.
 *
 * <p>The overlay uses one instrument panel and one caption plate rather than
 * floating labels or repeated boxes. Physical screen effects remain owned by
 * the presentation so the interface does not compete with the tank itself.</p>
 */
final class CryoWakeupHudRenderer {
    private static final Component SPEAKER_LABEL = Component.translatable(
            "cinematic.wildernessodysseyapi.cryo.speaker"
    );
    private static final Component BOOT_PRIMARY = Component.translatable(
            "cinematic.wildernessodysseyapi.cryo.boot.primary"
    );
    private static final Component BOOT_SECONDARY = Component.translatable(
            "cinematic.wildernessodysseyapi.cryo.boot.secondary"
    );

    private static final int PANEL_BACKGROUND = 0xD90A1112;
    private static final int PANEL_INSET = 0xB70D1819;
    private static final int FRAME = 0xA0567774;
    private static final int TEXT_PRIMARY = 0xFFE4F3F0;
    private static final int TEXT_SECONDARY = 0xFF9CB5B1;
    private static final int ACCENT = 0xFF65D6CC;
    private static final int WARNING = 0xFFF06B72;

    private CryoWakeupHudRenderer() {
    }

    static void renderBoot(GuiGraphics graphics, int width, int height, float progress) {
        Font font = Minecraft.getInstance().font;
        CryoWakeupHudLayout.Bounds panel = CryoWakeupHudLayout.boot(width, height);
        int alpha = Mth.clamp(Math.round(smooth(progress) * 255.0F), 0, 255);
        int accent = (alpha << 24) | (ACCENT & 0xFFFFFF);
        int primary = (alpha << 24) | (TEXT_PRIMARY & 0xFFFFFF);
        int secondary = (alpha << 24) | (TEXT_SECONDARY & 0xFFFFFF);

        graphics.fill(panel.x(), panel.y(), panel.right(), panel.bottom(), PANEL_BACKGROUND);
        graphics.fill(panel.x(), panel.y(), panel.x() + 2, panel.bottom(), accent);
        drawPanelFrame(graphics, panel, (alpha << 24) | (FRAME & 0xFFFFFF));
        graphics.drawString(font, BOOT_PRIMARY, panel.x() + 12, panel.y() + 8, primary, false);
        if (progress >= 0.22F) {
            graphics.drawString(font, BOOT_SECONDARY, panel.x() + 12, panel.y() + 21, secondary, false);
        }

        int trackX = panel.x() + 12;
        int trackY = panel.bottom() - 12;
        int trackWidth = panel.width() - 24;
        graphics.fill(trackX, trackY, trackX + trackWidth, trackY + 2, 0x70405B59);
        graphics.fill(trackX, trackY, trackX + Math.round(trackWidth * progress), trackY + 2, accent);
        Component percentage = Component.translatable(
                "cinematic.wildernessodysseyapi.cryo.boot.progress",
                Math.round(progress * 100.0F)
        );
        int percentageWidth = font.width(percentage);
        graphics.drawString(
                font,
                percentage,
                panel.right() - percentageWidth - 12,
                panel.bottom() - 25,
                secondary,
                false
        );
    }

    static void renderTelemetry(
            GuiGraphics graphics,
            int screenWidth,
            int screenHeight,
            ResourceLocation stage,
            float progress,
            float sequenceTime,
            float warningAlpha
    ) {
        CryoWakeupHudLayout.telemetry(screenWidth, screenHeight).ifPresent(panel -> {
            Font font = Minecraft.getInstance().font;
            boolean warning = warningAlpha > 0.04F;
            int accent = warning ? WARNING : ACCENT;

            graphics.fill(panel.x(), panel.y(), panel.right(), panel.bottom(), PANEL_BACKGROUND);
            graphics.fill(panel.x() + 3, panel.y() + 3, panel.right() - 3, panel.bottom() - 3, PANEL_INSET);
            graphics.fill(panel.x(), panel.y(), panel.x() + 2, panel.bottom(), accent);
            drawPanelFrame(graphics, panel, warning ? 0xC8A84B50 : FRAME);

            graphics.drawString(
                    font,
                    Component.translatable("cinematic.wildernessodysseyapi.cryo.telemetry.title"),
                    panel.x() + 11,
                    panel.y() + 7,
                    accent,
                    false
            );
            graphics.drawString(
                    font,
                    telemetryStatus(stage),
                    panel.x() + 11,
                    panel.y() + 20,
                    warning ? 0xFFFFA0A5 : TEXT_SECONDARY,
                    false
            );
            graphics.hLine(panel.x() + 11, panel.right() - 11, panel.y() + 32, 0x70567774);

            String temperature = String.format(
                    Locale.ROOT,
                    "%.1f",
                    CryoWakeupPresentationModel.coreTemperatureCelsius(stage, progress)
            );
            int heartRate = CryoWakeupPresentationModel.heartRateBpm(stage, progress);
            int oxygen = CryoWakeupPresentationModel.oxygenSaturation(stage, progress);
            drawMetric(
                    graphics,
                    font,
                    panel,
                    39,
                    "cinematic.wildernessodysseyapi.cryo.telemetry.label.temperature",
                    Component.translatable(
                            "cinematic.wildernessodysseyapi.cryo.telemetry.value.temperature",
                            temperature
                    ),
                    TEXT_PRIMARY
            );
            drawMetric(
                    graphics,
                    font,
                    panel,
                    53,
                    "cinematic.wildernessodysseyapi.cryo.telemetry.label.heart_rate",
                    Component.translatable(
                            "cinematic.wildernessodysseyapi.cryo.telemetry.value.heart_rate",
                            heartRate
                    ),
                    TEXT_PRIMARY
            );
            drawMetric(
                    graphics,
                    font,
                    panel,
                    67,
                    "cinematic.wildernessodysseyapi.cryo.telemetry.label.oxygen",
                    Component.translatable(
                            "cinematic.wildernessodysseyapi.cryo.telemetry.value.oxygen",
                            oxygen
                    ),
                    oxygen < 90 ? 0xFFFF959B : 0xFFA4E8CB
            );

            int waveformY = panel.y() + 92;
            graphics.hLine(panel.x() + 11, panel.right() - 11, waveformY, 0x55405B59);
            renderWaveform(
                    graphics,
                    panel.x() + 11,
                    waveformY,
                    panel.width() - 22,
                    heartRate,
                    sequenceTime,
                    accent
            );

            float thermalProgress = Mth.clamp(
                    (CryoWakeupPresentationModel.coreTemperatureCelsius(stage, progress) - 8.2F) / 28.4F,
                    0.0F,
                    1.0F
            );
            int thermalY = panel.bottom() - 7;
            graphics.fill(panel.x() + 11, thermalY, panel.right() - 11, thermalY + 1, 0x70405B59);
            graphics.fill(
                    panel.x() + 11,
                    thermalY,
                    panel.x() + 11 + Math.round((panel.width() - 22) * thermalProgress),
                    thermalY + 1,
                    accent
            );
        });
    }

    static void renderSubtitle(
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
        int splitWidth = Math.max(120, Math.min(width - 64, 500));
        List<FormattedCharSequence> lines = minecraft.font.split(subtitle, splitWidth);
        int contentWidth = minecraft.font.width(SPEAKER_LABEL);
        for (FormattedCharSequence line : lines) {
            contentWidth = Math.max(contentWidth, minecraft.font.width(line));
        }
        CryoWakeupHudLayout.Bounds panel = CryoWakeupHudLayout.subtitle(
                width,
                height,
                contentWidth,
                lines.size()
        );
        int backgroundAlpha = Mth.clamp(Math.round(alpha * 214.0F), 0, 214);
        int frameAlpha = Mth.clamp(Math.round(alpha * 150.0F), 0, 150);
        int textAlpha = Mth.clamp(Math.round(alpha * 255.0F), 0, 255);

        graphics.fill(
                panel.x(),
                panel.y(),
                panel.right(),
                panel.bottom(),
                (backgroundAlpha << 24) | 0x091112
        );
        graphics.fill(
                panel.x(),
                panel.y(),
                panel.x() + 2,
                panel.bottom(),
                (textAlpha << 24) | (ACCENT & 0xFFFFFF)
        );
        graphics.hLine(
                panel.x() + 2,
                panel.right() - 1,
                panel.y(),
                (frameAlpha << 24) | (FRAME & 0xFFFFFF)
        );
        graphics.drawString(
                minecraft.font,
                SPEAKER_LABEL,
                panel.x() + 11,
                panel.y() + 7,
                (textAlpha << 24) | (ACCENT & 0xFFFFFF),
                false
        );
        for (int i = 0; i < lines.size(); i++) {
            graphics.drawString(
                    minecraft.font,
                    lines.get(i),
                    panel.x() + 11,
                    panel.y() + 20 + i * 11,
                    (textAlpha << 24) | (TEXT_PRIMARY & 0xFFFFFF),
                    false
            );
        }
    }

    private static void drawMetric(
            GuiGraphics graphics,
            Font font,
            CryoWakeupHudLayout.Bounds panel,
            int yOffset,
            String labelKey,
            Component value,
            int valueColor
    ) {
        Component label = Component.translatable(labelKey);
        int y = panel.y() + yOffset;
        graphics.drawString(font, label, panel.x() + 11, y, TEXT_SECONDARY, false);
        graphics.drawString(font, value, panel.right() - 11 - font.width(value), y, valueColor, false);
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

    private static void drawPanelFrame(
            GuiGraphics graphics,
            CryoWakeupHudLayout.Bounds panel,
            int color
    ) {
        int right = panel.right() - 1;
        int bottom = panel.bottom() - 1;
        graphics.hLine(panel.x(), right, panel.y(), color);
        graphics.hLine(panel.x(), right, bottom, color);
        graphics.vLine(panel.x(), panel.y(), bottom, color);
        graphics.vLine(right, panel.y(), bottom, color);
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

    private static float smooth(float value) {
        float t = Mth.clamp(value, 0.0F, 1.0F);
        return t * t * (3.0F - 2.0F * t);
    }
}
