package com.thunder.wildernessodysseyapi.cinematic.client;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.client.event.ViewportEvent;
import org.jetbrains.annotations.Nullable;

/** Client-only camera, overlay, and completion presentation for one server sequence id. */
public interface ClientCinematicPresentation {
    ResourceLocation sequenceId();

    boolean recognizesStage(ResourceLocation stageId);

    void applyCamera(CinematicClientController state, ViewportEvent.ComputeCameraAngles event);

    void renderOverlay(
            CinematicClientController state,
            GuiGraphics graphics,
            int width,
            int height,
            float partialTick
    );

    /** Optional objective or handoff text shown after normal sequence completion. */
    default @Nullable Component completionMessage() {
        return null;
    }

    default int completionMessageTicks() {
        return 100;
    }
}
