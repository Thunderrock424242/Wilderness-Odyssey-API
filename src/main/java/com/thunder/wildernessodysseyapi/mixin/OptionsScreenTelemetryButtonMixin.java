package com.thunder.wildernessodysseyapi.mixin;

import com.thunder.wildernessodysseyapi.ModPackPatches.telemetry.TelemetryConsentScreen;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.events.ContainerEventHandler;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.options.OptionsScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;
import java.util.Set;

@Mixin(OptionsScreen.class)
public abstract class OptionsScreenTelemetryButtonMixin extends Screen {
    private static final Set<String> TELEMETRY_BUTTON_KEYS = Set.of(
            "options.telemetry",
            "options.telemetry.link"
    );

    protected OptionsScreenTelemetryButtonMixin(Component title) {
        super(title);
    }

    @Inject(method = "init", at = @At("TAIL"))
    private void wildernessOdysseyApi$replaceTelemetryButton(CallbackInfo ci) {
        AbstractWidget telemetryWidget = findTelemetryWidget();
        if (telemetryWidget == null || this.minecraft == null) {
            return;
        }

        int x = telemetryWidget.getX();
        int y = telemetryWidget.getY();
        int width = telemetryWidget.getWidth();
        int height = telemetryWidget.getHeight();
        removeWidget(telemetryWidget);

        addRenderableWidget(Button.builder(
                        Component.translatable("screen.wildernessodysseyapi.telemetry.title"),
                        button -> this.minecraft.setScreen(new TelemetryConsentScreen(this)))
                .bounds(x, y, width, height)
                .build());
    }

    private AbstractWidget findTelemetryWidget() {
        return findTelemetryWidget(this.children());
    }

    private AbstractWidget findTelemetryWidget(List<? extends GuiEventListener> listeners) {
        for (GuiEventListener listener : listeners) {
            if (listener instanceof AbstractWidget widget && isTelemetryWidget(widget)) {
                return widget;
            }
            if (listener instanceof ContainerEventHandler container) {
                AbstractWidget nested = findTelemetryWidget(container.children());
                if (nested != null) {
                    return nested;
                }
            }
        }
        return null;
    }

    private boolean isTelemetryWidget(AbstractWidget widget) {
        if (widget.getMessage().getContents() instanceof TranslatableContents contents) {
            return TELEMETRY_BUTTON_KEYS.contains(contents.getKey());
        }
        return false;
    }
}
