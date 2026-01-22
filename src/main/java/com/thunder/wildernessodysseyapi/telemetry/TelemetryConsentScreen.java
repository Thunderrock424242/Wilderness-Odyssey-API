package com.thunder.wildernessodysseyapi.telemetry;

import com.thunder.wildernessodysseyapi.telemetry.TelemetryConsentStore.ConsentDecision;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

import java.util.Collections;
import java.util.List;

/**
 * Simple consent dialog for telemetry sharing.
 */
public class TelemetryConsentScreen extends Screen {
    private static final int BUTTON_WIDTH = 180;
    private static final int BUTTON_HEIGHT = 20;
    private static final int TEXT_PADDING = 10;
    private static final int BUTTON_TOP_MARGIN = 18;
    private static final int MAX_TEXT_WIDTH = 340;

    private List<FormattedCharSequence> descriptionLines = Collections.emptyList();
    private int titleY;
    private int descriptionY;
    private int statusY;
    private int buttonY;
    private ConsentDecision currentDecision = ConsentDecision.ACCEPTED;
    private Button toggleButton;

    public TelemetryConsentScreen() {
        super(Component.translatable("screen.wildernessodysseyapi.telemetry.title"));
    }

    @Override
    protected void init() {
        ConsentDecision storedDecision = TelemetryConsentConfig.decision();
        this.currentDecision = storedDecision == ConsentDecision.UNKNOWN
                ? ConsentDecision.ACCEPTED
                : storedDecision;
        int centerX = this.width / 2;
        int maxTextWidth = Math.min(this.width - 40, MAX_TEXT_WIDTH);
        this.descriptionLines = this.font.split(
                Component.translatable("screen.wildernessodysseyapi.telemetry.description"),
                maxTextWidth
        );
        int descriptionHeight = this.descriptionLines.size() * this.font.lineHeight;
        int textBlockHeight = this.font.lineHeight + TEXT_PADDING + descriptionHeight + TEXT_PADDING + this.font.lineHeight;
        int buttonGap = BUTTON_TOP_MARGIN + TEXT_PADDING + 4;
        int totalHeight = textBlockHeight + buttonGap + (BUTTON_HEIGHT * 2) + 6;
        int startY = Math.max(20, (this.height - totalHeight) / 2);
        this.titleY = startY;
        this.descriptionY = this.titleY + this.font.lineHeight + TEXT_PADDING;
        this.statusY = this.descriptionY + descriptionHeight + TEXT_PADDING;
        this.buttonY = this.statusY + this.font.lineHeight + buttonGap;

        this.toggleButton = addRenderableWidget(Button.builder(toggleLabel(), button -> toggleDecision())
                .bounds(centerX - (BUTTON_WIDTH / 2), this.buttonY, BUTTON_WIDTH, BUTTON_HEIGHT)
                .build());

        addRenderableWidget(Button.builder(
                        Component.translatable("gui.done"),
                        button -> applyDecision())
                .bounds(centerX - (BUTTON_WIDTH / 2), this.buttonY + BUTTON_HEIGHT + 6, BUTTON_WIDTH, BUTTON_HEIGHT)
                .build());
    }

    private Component toggleLabel() {
        return Component.translatable(
                currentDecision == ConsentDecision.ACCEPTED
                        ? "screen.wildernessodysseyapi.telemetry.toggle.off"
                        : "screen.wildernessodysseyapi.telemetry.toggle.on"
        );
    }

    private Component statusLabel() {
        return Component.translatable(
                "screen.wildernessodysseyapi.telemetry.status",
                Component.translatable(
                        currentDecision == ConsentDecision.ACCEPTED
                                ? "screen.wildernessodysseyapi.telemetry.status.on"
                                : "screen.wildernessodysseyapi.telemetry.status.off"
                )
        );
    }

    private void toggleDecision() {
        this.currentDecision = this.currentDecision == ConsentDecision.ACCEPTED
                ? ConsentDecision.DECLINED
                : ConsentDecision.ACCEPTED;
        if (this.toggleButton != null) {
            this.toggleButton.setMessage(toggleLabel());
        }
    }

    private void applyDecision() {
        TelemetryConsentConfig.setDecision(currentDecision);
        sendConsentCommand(currentDecision);
        Minecraft.getInstance().setScreen(null);
    }

    private void sendConsentCommand(ConsentDecision decision) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.player.connection == null) {
            return;
        }
        String command = decision == ConsentDecision.ACCEPTED ? "telemetryconsent accept" : "telemetryconsent decline";
        minecraft.player.connection.sendCommand(command);
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(guiGraphics, mouseX, mouseY, partialTick);
        int centerX = this.width / 2;
        guiGraphics.drawCenteredString(this.font, this.title, centerX, this.titleY, 0xFFFFFFFF);
        int lineY = this.descriptionY;
        for (FormattedCharSequence line : this.descriptionLines) {
            int lineX = centerX - this.font.width(line) / 2;
            guiGraphics.drawString(this.font, line, lineX, lineY, 0xFFDDDDDD, false);
            lineY += this.font.lineHeight;
        }
        guiGraphics.drawCenteredString(this.font, statusLabel(), centerX, this.statusY, 0xFFFFFFFF);
        super.render(guiGraphics, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean shouldCloseOnEsc() {
        return false;
    }
}
