package com.thunder.wildernessodysseyapi.playtest.diagnostics.client;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** Explicit local consent and private invitation entry; no secrets are ever sent as chat commands. */
final class PlaytestEnrollmentScreen extends Screen {
    private final Screen previous;
    private EditBox origin;
    private EditBox invitation;

    PlaytestEnrollmentScreen(Screen previous) {
        super(Component.translatable("screen.wildernessodysseyapi.playtest.title"));
        this.previous = previous;
    }

    @Override protected void init() {
        int left = width / 2 - 140;
        int top = Math.max(66, height / 2 - 76);
        origin = new EditBox(font, left, top + 14, 280, 20, Component.translatable("screen.wildernessodysseyapi.playtest.origin"));
        origin.setMaxLength(300);
        origin.setValue(PlaytestDiagnosticEvents.origin());
        addRenderableWidget(origin);
        invitation = new EditBox(font, left, top + 52, 280, 20, Component.translatable("screen.wildernessodysseyapi.playtest.invitation"));
        invitation.setMaxLength(256);
        addRenderableWidget(invitation);
        addRenderableWidget(Button.builder(Component.translatable("screen.wildernessodysseyapi.playtest.accept"), button -> {
            String code = invitation.getValue();
            invitation.setValue("");
            PlaytestDiagnosticEvents.enroll(origin.getValue(), code);
        }).bounds(left, top + 84, 280, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("screen.wildernessodysseyapi.playtest.stop"), button -> PlaytestDiagnosticEvents.stop())
                .bounds(left, top + 108, 138, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("gui.back"), button -> onClose()).bounds(left + 142, top + 108, 138, 20).build());
    }

    @Override public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics, mouseX, mouseY, partialTick);
        super.render(graphics, mouseX, mouseY, partialTick);
        int left = width / 2 - 140;
        int top = Math.max(66, height / 2 - 76);
        graphics.drawCenteredString(font, title, width / 2, 12, 0xFFFFFF);
        graphics.drawWordWrap(font, Component.translatable("screen.wildernessodysseyapi.playtest.notice"), left, 26, 280, 0xCCCCCC);
        graphics.drawString(font, Component.translatable("screen.wildernessodysseyapi.playtest.origin"), left, top + 3, 0xFFFFFF);
        graphics.drawString(font, Component.translatable("screen.wildernessodysseyapi.playtest.invitation"), left, top + 41, 0xFFFFFF);
        graphics.drawWordWrap(font, Component.literal(PlaytestDiagnosticEvents.status()), left, top + 136, 280, 0xEEEEAA);
        String message = PlaytestDiagnosticEvents.enrollmentMessage();
        if (!message.startsWith("Loading")) graphics.drawWordWrap(font, Component.literal(message), left, top + 156, 280, 0xAAAAAA);
    }

    @Override public void onClose() { if (minecraft != null) minecraft.setScreen(previous); }
}
