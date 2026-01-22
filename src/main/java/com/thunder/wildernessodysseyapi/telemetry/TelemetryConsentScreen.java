package com.thunder.wildernessodysseyapi.telemetry;

import com.thunder.wildernessodysseyapi.telemetry.TelemetryConsentStore.ConsentDecision;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * Simple consent dialog for telemetry sharing.
 */
public class TelemetryConsentScreen extends Screen {
    private static final int BUTTON_WIDTH = 160;
    private static final int BUTTON_HEIGHT = 20;

    public TelemetryConsentScreen() {
        super(Component.translatable("screen.wildernessodysseyapi.telemetry.title"));
    }

    @Override
    protected void init() {
        int centerX = this.width / 2;
        int buttonY = this.height / 2 + 30;

        addRenderableWidget(Button.builder(
                        Component.translatable("screen.wildernessodysseyapi.telemetry.accept"),
                        button -> handleChoice(ConsentDecision.ACCEPTED))
                .bounds(centerX - BUTTON_WIDTH - 10, buttonY, BUTTON_WIDTH, BUTTON_HEIGHT)
                .build());

        addRenderableWidget(Button.builder(
                        Component.translatable("screen.wildernessodysseyapi.telemetry.decline"),
                        button -> handleChoice(ConsentDecision.DECLINED))
                .bounds(centerX + 10, buttonY, BUTTON_WIDTH, BUTTON_HEIGHT)
                .build());
    }

    private void handleChoice(ConsentDecision decision) {
        TelemetryConsentConfig.setDecision(decision);
        sendConsentCommand(decision);
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
        guiGraphics.drawCenteredString(this.font, this.title, this.width / 2, this.height / 2 - 40, 0xFFFFFFFF);
        guiGraphics.drawCenteredString(
                this.font,
                Component.translatable("screen.wildernessodysseyapi.telemetry.description"),
                this.width / 2,
                this.height / 2 - 15,
                0xFFDDDDDD
        );
        super.render(guiGraphics, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean shouldCloseOnEsc() {
        return false;
    }
}
