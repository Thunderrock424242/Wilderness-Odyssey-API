package com.wildernessodyssey.client.gui;

import com.wildernessodyssey.client.hardware.HardwareRequirementChecker;
import com.wildernessodyssey.client.hardware.HardwareRequirementConfig;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Simple screen that presents the hardware requirement evaluation to the user.
 */
public class HardwareRequirementScreen extends Screen {
    private static final int COLOR_OK = 0x4CAF50;
    private static final int COLOR_WARN = 0xFF5555;
    private static final int COLOR_UNKNOWN = 0xFFA000;

    private final HardwareRequirementChecker checker;
    private HardwareRequirementChecker.HardwareSnapshot snapshot;
    private EnumMap<HardwareRequirementConfig.Tier, HardwareRequirementChecker.TierEvaluation> evaluations;

    public HardwareRequirementScreen(HardwareRequirementChecker checker) {
        super(Component.translatable("screen.wildernessodyssey.hardware.title"));
        this.checker = Objects.requireNonNull(checker);
    }

    @Override
    protected void init() {
        snapshot = checker.refresh();
        evaluations = checker.evaluateAll(snapshot);

        addRenderableWidget(Button.builder(Component.translatable("gui.done"), button -> onClose())
            .bounds((this.width - 200) / 2, this.height - 40, 200, 20)
            .build());
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(graphics, mouseX, mouseY, partialTick);
        graphics.drawCenteredString(this.font, this.title, this.width / 2, 20, 0xFFFFFF);

        int y = 50;
        y = renderCurrentHardware(graphics, y);
        y += 12;
        renderTierStatuses(graphics, y);
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    private int renderCurrentHardware(GuiGraphics graphics, int y) {
        graphics.drawString(this.font, Component.translatable("screen.wildernessodyssey.hardware.section.current"), 20, y, 0xFFFFFF);
        y += this.font.lineHeight + 2;

        graphics.drawString(this.font,
            Component.translatable("screen.wildernessodyssey.hardware.metric.cpu.detail", snapshot.cpuName(), snapshot.cpuCores()),
            20, y, 0xCCCCCC);
        y += this.font.lineHeight + 2;

        graphics.drawString(this.font,
            Component.translatable("screen.wildernessodyssey.hardware.metric.ram.detail", formatMegabytes(snapshot.systemRamMb())),
            20, y, 0xCCCCCC);
        y += this.font.lineHeight + 2;

        graphics.drawString(this.font,
            Component.translatable("screen.wildernessodyssey.hardware.metric.vram.detail", formatMegabytes(snapshot.vramMb())),
            20, y, 0xCCCCCC);
        y += this.font.lineHeight + 2;

        graphics.drawString(this.font,
            Component.translatable("screen.wildernessodyssey.hardware.metric.gpu.detail", defaultString(snapshot.gpuVendor()), defaultString(snapshot.gpuRenderer())),
            20, y, 0xCCCCCC);
        return y + this.font.lineHeight;
    }

    private void renderTierStatuses(GuiGraphics graphics, int y) {
        graphics.drawString(this.font, Component.translatable("screen.wildernessodyssey.hardware.section.requirements"), 20, y, 0xFFFFFF);
        y += this.font.lineHeight + 4;

        for (Map.Entry<HardwareRequirementConfig.Tier, HardwareRequirementChecker.TierEvaluation> entry : evaluations.entrySet()) {
            HardwareRequirementConfig.Tier tier = entry.getKey();
            HardwareRequirementChecker.TierEvaluation evaluation = entry.getValue();

            Component tierLabel = Component.translatable("hardware.wildernessodyssey.tier." + tier.name().toLowerCase());
            EnumSet<HardwareRequirementChecker.Metric> failing = evaluation.failingMetrics();
            EnumSet<HardwareRequirementChecker.Metric> unknown = evaluation.unknownMetrics();

            Component message;
            int color;
            if (!failing.isEmpty()) {
                String issues = failing.stream().map(this::describeMetric).collect(Collectors.joining(", "));
                message = Component.translatable("hardware.wildernessodyssey.status.warning", tierLabel, issues);
                color = COLOR_WARN;
            } else if (!unknown.isEmpty()) {
                String issues = unknown.stream().map(this::describeMetric).collect(Collectors.joining(", "));
                message = Component.translatable("hardware.wildernessodyssey.status.partial", tierLabel, issues);
                color = COLOR_UNKNOWN;
            } else {
                message = Component.translatable("hardware.wildernessodyssey.status.ok", tierLabel);
                color = COLOR_OK;
            }

            graphics.drawString(this.font, message, 20, y, color);
            y += this.font.lineHeight + 2;

            HardwareRequirementConfig.HardwareRequirementTier requirements = checker.config().getTier(tier).orElse(null);
            if (requirements != null) {
                List<Component> requirementLines = buildRequirementSummary(requirements);
                for (Component line : requirementLines) {
                    graphics.drawString(this.font, line, 30, y, 0x888888);
                    y += this.font.lineHeight + 1;
                }
            }

            y += 6;
        }
    }

    private List<Component> buildRequirementSummary(HardwareRequirementConfig.HardwareRequirementTier requirements) {
        List<Component> lines = new ArrayList<>();
        if (requirements.minCpuCores() > 0) {
            lines.add(Component.translatable("screen.wildernessodyssey.hardware.requirement.cpu", requirements.minCpuCores()));
        }
        if (requirements.minRamMb() > 0) {
            lines.add(Component.translatable("screen.wildernessodyssey.hardware.requirement.ram", formatMegabytes(requirements.minRamMb())));
        }
        if (requirements.minVramMb() > 0) {
            lines.add(Component.translatable("screen.wildernessodyssey.hardware.requirement.vram", formatMegabytes(requirements.minVramMb())));
        }
        requirements.shaderPack().ifPresent(shaderPack ->
            lines.add(Component.translatable("screen.wildernessodyssey.hardware.requirement.shaderpack", shaderPack)));
        if (requirements.hasGpuRequirement()) {
            if (!requirements.gpuVendorKeywords().isEmpty()) {
                String vendors = String.join(", ", requirements.gpuVendorKeywords());
                lines.add(Component.translatable("screen.wildernessodyssey.hardware.requirement.gpu.vendor", vendors));
            }
            if (!requirements.gpuRendererKeywords().isEmpty()) {
                String renderers = String.join(", ", requirements.gpuRendererKeywords());
                lines.add(Component.translatable("screen.wildernessodyssey.hardware.requirement.gpu.renderer", renderers));
            }
        }
        return lines;
    }

    private String describeMetric(HardwareRequirementChecker.Metric metric) {
        return Component.translatable(metric.translationKey()).getString();
    }

    private static String formatMegabytes(long mb) {
        if (mb <= 0) {
            return Component.translatable("hardware.wildernessodyssey.metric.unknown").getString();
        }
        if (mb >= 1024) {
            double gb = mb / 1024.0;
            return String.format("%.1f GB", gb);
        }
        return mb + " MB";
    }

    private static String defaultString(String value) {
        return value == null || value.isBlank() ? Component.translatable("hardware.wildernessodyssey.metric.unknown").getString() : value;
    }
}
