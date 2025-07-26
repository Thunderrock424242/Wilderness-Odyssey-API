package com.thunder.wildernessodysseyapi.ModPackPatches.client;

import com.thunder.wildernessodysseyapi.ModConflictChecker.Util.LoggerUtil;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public class WorldVersionWarningScreen extends Screen {
    private final Runnable onProceed;
    private final Runnable onCancel;
    private final String oldVersion;
    private final String newVersion;
    private final String updateType;

    public WorldVersionWarningScreen(String oldVersion, String newVersion, Runnable onProceed, Runnable onCancel) {
        super(Component.literal("World Version Warning"));
        this.oldVersion = oldVersion;
        this.newVersion = newVersion;
        this.onProceed = onProceed;
        this.onCancel = onCancel;
        this.updateType = WorldVersionChecker.getUpdateType(oldVersion, newVersion);
    }

    @Override
    protected void init() {
        int boxWidth = 420;
        int boxHeight = 200;
        int buttonWidth = 160;
        int buttonHeight = 20;
        int margin = 14;
        int boxX = (this.width - boxWidth) / 2;
        int boxY = (this.height - boxHeight) / 2;
        int buttonY = boxY + boxHeight - buttonHeight - margin;

        this.addRenderableWidget(Button.builder(
                Component.literal("Operator must run /updateworldversion"),
                b -> {
                    if (onProceed != null) {
                        LoggerUtil.log(LoggerUtil.ConflictSeverity.INFO, "[WorldVersionWarningScreen] User chose to proceed and continue playing.");
                        onProceed.run();
                    }
                }
        ).bounds(boxX + margin, buttonY, buttonWidth, buttonHeight).build());

        this.addRenderableWidget(Button.builder(
                Component.literal("Cancel (Return to Title)"),
                button -> {
                    if (onCancel != null) {
                        LoggerUtil.log(LoggerUtil.ConflictSeverity.INFO, "[WorldVersionWarningScreen] User chose to cancel and return to title screen.");
                        onCancel.run();
                    }
                }
        ).bounds(boxX + boxWidth - buttonWidth - margin, buttonY, buttonWidth, buttonHeight).build());
    }
    @Override
    public void render(GuiGraphics gfx, int mouseX, int mouseY, float partialTicks) {

        super.render(gfx, mouseX, mouseY, partialTicks);

        int boxWidth = 420;
        int boxHeight = 200;
        int boxY = (this.height - boxHeight) / 2;


        gfx.drawCenteredString(this.font, Component.literal("World Version Warning!"), this.width / 2, boxY + 16, 0xFFFFFFFF);
        gfx.drawCenteredString(this.font, Component.literal(updateType + " Update Detected"), this.width / 2, boxY + 35, 0xFFFFFFFF);
        gfx.drawCenteredString(this.font, Component.literal("World Version: " + oldVersion + " → " + newVersion), this.width / 2, boxY + 52, 0xFFFFFFFF);
        gfx.drawCenteredString(this.font, Component.literal("Your world is outdated and needs updating."), this.width / 2, boxY + 70, 0xFFEEEEEE);
        gfx.drawCenteredString(this.font, Component.literal("BACKUP YOUR WORLD BEFORE PROCEEDING"), this.width / 2, boxY + 87, 0xFFFF4444);
        gfx.drawCenteredString(this.font, Component.literal("Only operators can update the world version."), this.width / 2, boxY + 104, 0xFFDDDDDD);
        gfx.drawCenteredString(this.font, Component.literal("Run /updateworldversion in chat if you are an operator."), this.width / 2, boxY + 121, 0xFFDDDDDD);
        gfx.drawCenteredString(this.font, Component.literal("World generation and structures may change."), this.width / 2, boxY + 138, 0xFFDDDDDD);
        gfx.drawCenteredString(this.font, Component.literal("For bugs, test in a fresh world before reporting."), this.width / 2, boxY + 155, 0xFFDDDDDD);
    }

    @Override
    protected void renderMenuBackground(GuiGraphics gfx) {
        gfx.fill(0, 0, this.width, this.height, 0xFF181818);
        int boxWidth = 420;
        int boxHeight = 200;
        int boxX = (this.width - boxWidth) / 2;
        int boxY = (this.height - boxHeight) / 2;
        gfx.fill(boxX, boxY, boxX + boxWidth, boxY + boxHeight, 0xFF333333);

        int borderColor = 0xFFAAAAAA;
        gfx.fill(boxX, boxY, boxX + boxWidth, boxY + 2, borderColor);
        gfx.fill(boxX, boxY + boxHeight - 2, boxX + boxWidth, boxY + boxHeight, borderColor);
        gfx.fill(boxX, boxY, boxX + 2, boxY + boxHeight, borderColor);
        gfx.fill(boxX + boxWidth - 2, boxY, boxX + boxWidth, boxY + boxHeight, borderColor);

        super.renderMenuBackground(gfx);
    }

    @Override
    public boolean shouldCloseOnEsc() {
            return false;
    }

    @Override
    public boolean isPauseScreen() {
        return true;
    }
}