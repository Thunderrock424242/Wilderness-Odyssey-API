package com.thunder.wildernessodysseyapi.WorldVersionChecker.client.gui;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;
import net.minecraft.client.gui.components.MultiLineLabel;

public class OutdatedWorldScreen extends Screen {
    private MultiLineLabel label = MultiLineLabel.EMPTY;
    private final Runnable onContinue;

    public OutdatedWorldScreen(Runnable onContinue) {
        super(Component.literal("Warning: Outdated World"));
        this.onContinue = onContinue;
    }

    @Override
    protected void init() {
        this.label = MultiLineLabel.create(this.font, Component.literal("""
            §cThis world was created with an older version of Wilderness Odyssey.
            
            Minecraft will attempt to update it when you play, but issues may occur.
            
            §ePlease test any bugs in a fresh world before reporting them.

            §7Backup your world before continuing.
            """), this.width - 40);

        this.addRenderableWidget(Button.builder(Component.literal("I Understand, Continue"), btn -> {
            this.onClose();
            onContinue.run();
        }).bounds(this.width / 2 - 100, this.height - 40, 200, 20).build());
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        this.renderDirtBackground(guiGraphics); // Classic Minecraft dirt background
        super.render(guiGraphics, mouseX, mouseY, partialTick);
        this.label.renderCentered(guiGraphics, this.width / 2, 40);
    }

    @Override
    public boolean shouldCloseOnEsc() {
        return false;
    }
}
