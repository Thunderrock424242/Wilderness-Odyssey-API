package com.thunder.wildernessodysseyapi.GlobalChat;

import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.network.chat.Component;

public class CustomChatScreen extends ChatScreen {
    private boolean inGlobalChat = false;
    private Button switchTabButton;

    public CustomChatScreen() {
        super("");
    }

    @Override
    protected void init() {
        super.init();

        // Add a button to switch chat tabs
        switchTabButton = Button.builder(Component.literal("Switch to Global"), button -> {
            inGlobalChat = !inGlobalChat;
            switchTabButton.setMessage(Component.literal(inGlobalChat ? "Switch to Local" : "Switch to Global"));
        }).pos(this.width - 110, this.height - 40).size(100, 20).build();

        this.addRenderableWidget(switchTabButton);
    }

    @Override
    public void handleChatInput(String input, boolean addToHistory) {
        if (input.isEmpty()) return;

        if (inGlobalChat) {
            ChatClient.sendMessage(input);  // Send to the global chat server
        } else {
            // Send to Minecraft's built-in chat
            super.handleChatInput(input, addToHistory);
        }
    }

    @Override
    public void render(net.minecraft.client.gui.GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTicks) {
        super.render(guiGraphics, mouseX, mouseY, partialTicks);

        // Corrected drawCenteredString usage
        guiGraphics.drawCenteredString(this.font,
                Component.literal(inGlobalChat ? "Global Chat" : "Local Chat"),
                this.width / 2,
                this.height - 55,
                0xFFFFFF
        );
    }
}
