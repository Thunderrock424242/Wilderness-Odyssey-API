package com.thunder.wildernessodysseyapi.mixin;

import com.thunder.wildernessodysseyapi.telemetry.TelemetryConsentScreen;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.options.OptionsScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(OptionsScreen.class)
public abstract class OptionsScreenTelemetryButtonMixin extends Screen {
    private static final String TELEMETRY_BUTTON_KEY = "options.telemetry";

    protected OptionsScreenTelemetryButtonMixin(Component title) {
        super(title);
    }

    @Inject(method = "init", at = @At("TAIL"))
    private void wildernessOdysseyApi$replaceTelemetryButton(CallbackInfo ci) {
        Button telemetryButton = findTelemetryButton();
        if (telemetryButton == null || this.minecraft == null) {
            return;
        }

        int x = telemetryButton.getX();
        int y = telemetryButton.getY();
        int width = telemetryButton.getWidth();
        int height = telemetryButton.getHeight();
        removeWidget(telemetryButton);

        addRenderableWidget(Button.builder(
                        Component.translatable("screen.wildernessodysseyapi.telemetry.title"),
                        button -> this.minecraft.setScreen(new TelemetryConsentScreen()))
                .bounds(x, y, width, height)
                .build());
    }

    private Button findTelemetryButton() {
        for (GuiEventListener listener : this.children()) {
            if (listener instanceof Button button && isTelemetryButton(button)) {
                return button;
            }
        }
        return null;
    }

    private boolean isTelemetryButton(Button button) {
        if (button.getMessage().getContents() instanceof TranslatableContents contents) {
            return TELEMETRY_BUTTON_KEY.equals(contents.getKey());
        }
        return false;
    }
}
