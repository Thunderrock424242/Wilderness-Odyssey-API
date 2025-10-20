package com.wildernessodyssey.client.gui;

import com.mojang.blaze3d.platform.InputConstants;
import com.wildernessodyssey.client.hardware.HardwareRequirementChecker;
import com.wildernessodyssey.client.hardware.HardwareRequirementConfig;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Simple screen that presents the hardware requirement evaluation to the user.
 */
public class HardwareRequirementScreen extends Screen {
    private static final int COLOR_OK = 0x4CAF50;
    private static final int COLOR_WARN = 0xFF5555;
    private static final int COLOR_UNKNOWN = 0xFFA000;
    private static final int COLOR_INFO = 0x64B5F6;

    private final HardwareRequirementChecker checker;
    private HardwareRequirementChecker.HardwareSnapshot snapshot;
    private EnumMap<HardwareRequirementConfig.Tier, HardwareRequirementChecker.TierEvaluation> evaluations;
    private double scrollOffset;
    private int contentHeight;
    private int contentTop;
    private int contentBottom;
    private int contentLeft;
    private int contentRight;

    public HardwareRequirementScreen(HardwareRequirementChecker checker) {
        super(Component.translatable("screen.wildernessodyssey.hardware.title"));
        this.checker = Objects.requireNonNull(checker);
    }

    @Override
    protected void init() {
        snapshot = checker.refresh();
        evaluations = checker.evaluateAll(snapshot);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(graphics, mouseX, mouseY, partialTick);
        graphics.drawCenteredString(this.font, this.title, this.width / 2, 20, 0xFFFFFF);

        this.contentLeft = 20;
        this.contentRight = this.width - 20;
        this.contentTop = 48;
        this.contentBottom = this.height - 60;

        if (this.contentBottom <= this.contentTop) {
            this.contentBottom = this.contentTop + 1;
        }

        graphics.enableScissor(this.contentLeft, this.contentTop, this.contentRight, this.contentBottom);
        graphics.pose().pushPose();
        graphics.pose().translate(0.0, -this.scrollOffset, 0.0);

        int y = this.contentTop;
        y = renderCurrentHardware(graphics, y);
        y += 12;
        y = renderTierStatuses(graphics, y);

        graphics.pose().popPose();
        graphics.disableScissor();

        this.contentHeight = y - this.contentTop;
        clampScrollOffset();

        renderScrollBar(graphics);
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        graphics.fillGradient(0, 0, this.width, this.height, 0xCC101010, 0xCC101010);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (this.shouldCloseOnEsc() && keyCode == InputConstants.KEY_ESCAPE) {
            this.onClose();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
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

    private int renderTierStatuses(GuiGraphics graphics, int y) {
        graphics.drawString(this.font, Component.translatable("screen.wildernessodyssey.hardware.section.requirements"), 20, y, 0xFFFFFF);
        y += this.font.lineHeight + 4;

        Optional<HardwareRequirementConfig.Tier> bestTier = checker.selectHighestMeetingTier(evaluations);
        Component bestTierLabel = bestTier
            .map(tier -> Component.translatable("hardware.wildernessodyssey.tier." + tier.name().toLowerCase()))
            .orElse(null);

        if (bestTier.isPresent()) {
            graphics.drawString(this.font,
                Component.translatable("screen.wildernessodyssey.hardware.best_tier", bestTierLabel),
                20, y, COLOR_OK);
        } else {
            graphics.drawString(this.font,
                Component.translatable("screen.wildernessodyssey.hardware.best_tier.unknown"),
                20, y, COLOR_WARN);
        }
        y += this.font.lineHeight + 6;

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
            } else if (bestTier.isPresent() && bestTier.get() != tier && bestTierLabel != null
                && bestTier.get().ordinal() > tier.ordinal()) {
                message = Component.translatable("hardware.wildernessodyssey.status.exceeds", tierLabel, bestTierLabel);
                color = COLOR_INFO;
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
        return y;
    }

    private void renderScrollBar(GuiGraphics graphics) {
        int viewHeight = getViewHeight();
        if (this.contentHeight <= viewHeight || viewHeight <= 4) {
            return;
        }

        int scrollbarHeight = (int) Math.max(32.0, (double) viewHeight * viewHeight / this.contentHeight);
        scrollbarHeight = Math.min(scrollbarHeight, viewHeight - 4);
        int scrollbarTravel = viewHeight - scrollbarHeight;
        int scrollbarTop = this.contentTop + (int) (this.scrollOffset * scrollbarTravel / (this.contentHeight - viewHeight));
        int scrollbarRight = this.contentRight - 2;
        int scrollbarLeft = scrollbarRight - 4;

        graphics.fill(scrollbarLeft, this.contentTop, scrollbarRight, this.contentBottom, 0x66000000);
        graphics.fill(scrollbarLeft, scrollbarTop, scrollbarRight, scrollbarTop + scrollbarHeight, 0xCCFFFFFF);
    }

    private int getViewHeight() {
        return Math.max(1, this.contentBottom - this.contentTop);
    }

    private void clampScrollOffset() {
        int viewHeight = getViewHeight();
        int maxScroll = Math.max(0, this.contentHeight - viewHeight);
        this.scrollOffset = Mth.clamp(this.scrollOffset, 0.0, maxScroll);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double deltaX, double deltaY) {
        if (isMouseOverContent(mouseX, mouseY) && this.contentHeight > getViewHeight()) {
            double scrollDelta = deltaY != 0.0 ? deltaY : deltaX;
            this.scrollOffset = Mth.clamp(this.scrollOffset - scrollDelta * 10, 0.0, Math.max(0, this.contentHeight - getViewHeight()));
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, deltaX, deltaY);
    }

    private boolean isMouseOverContent(double mouseX, double mouseY) {
        return mouseX >= this.contentLeft && mouseX <= this.contentRight && mouseY >= this.contentTop && mouseY <= this.contentBottom;
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
