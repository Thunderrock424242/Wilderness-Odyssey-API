package com.thunder.wildernessodysseyapi.WorldVersionChecker.client.gui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.MultiLineLabel;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.TextColor;

public class WorldOutdatedGateScreen extends Screen {
    private final Runnable onProceed;
    private MultiLineLabel messageLines = MultiLineLabel.EMPTY;

    public WorldOutdatedGateScreen(Runnable onProceed) {
        super(Component.literal(""));
        this.onProceed = onProceed;
    }

    @Override
    protected void init() {
        // Prepare the multi-line explanation
        this.messageLines = MultiLineLabel.create(
                this.font,
                Component.literal(
                        """
                                This world was saved with an older version of Wilderness Odyssey.
                                
                                It may corrupt or crash. Please BACK UP your world first.
                                
                                Proceed at your own risk!"""
                ),
                this.width - 50
        );

        // “Proceed” button
        this.addRenderableWidget(Button.builder(
                        Component.literal("Proceed"),
                        btn -> {
                            onProceed.run();
                        })
                .bounds((width / 2) - 155, height - 50, 150, 20)
                .build()
        );

        // “Cancel” button
        this.addRenderableWidget(Button.builder(
                        Component.literal("Cancel"),
                        btn -> {
                            Minecraft.getInstance().getConnection()
                                    .disconnect(Component.literal("Join cancelled: world is outdated."));
                        })
                .bounds((width / 2) + 5, height - 50, 150, 20)
                .build()
        );
    }

    @Override
    public void render(GuiGraphics gui, int mouseX, int mouseY, float partialTick) {
        // 1) Dirt background
        this.renderBackground(gui, 0, 0, 0);

        // 2) Big red header text
        gui.drawCenteredString(
                this.font,
                Component.literal("Outdated World Detected").withStyle(style -> style.withColor(TextColor.fromRgb(0xFF5555))),
                this.width / 2,
                this.height / 4,
                0xFF0000
        );

        // 3) Explanatory multi-line text
        this.messageLines.renderCentered(gui, this.width / 2, (this.height / 4) + 30);

        // 4) Draw buttons
        super.render(gui, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean shouldCloseOnEsc() {
        return false; // must click one of the buttons
    }
}
