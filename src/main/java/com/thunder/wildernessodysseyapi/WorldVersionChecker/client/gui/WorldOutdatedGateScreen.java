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
        this.messageLines = MultiLineLabel.create(
                this.font,
                Component.literal(
                        "This world was saved with an older version of Wilderness Odyssey.\n\n" +
                                "It may corrupt or crash. Please BACK UP your world first.\n\n" +
                                "Proceed at your own risk!"
                ),
                this.width - 50
        );

        this.addRenderableWidget(Button.builder(
                Component.literal("Proceed"),
                btn -> onProceed.run()
        ).bounds((width/2)-155, height-50, 150, 20).build());

        this.addRenderableWidget(Button.builder(
                Component.literal("Cancel"),
                btn -> Minecraft.getInstance().getConnection().disconnect(
                        Component.literal("Join cancelled: outdated world.")
                )
        ).bounds((width/2)+5, height-50, 150, 20).build());
    }

    @Override
    public void render(GuiGraphics gui, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(gui, mouseX, mouseY, partialTick);
        gui.drawCenteredString(
                this.font,
                Component.literal("Outdated World Detected")
                        .withStyle(style -> style.withColor(TextColor.fromRgb(0xFF5555))),
                this.width/2,
                this.height/4,
                0xFF0000
        );
        this.messageLines.renderCentered(gui, this.width/2, (this.height/4)+30);
        super.render(gui, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean shouldCloseOnEsc() {
        return false;
    }
}
