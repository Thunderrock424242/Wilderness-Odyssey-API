package com.thunder.wildernessodysseyapi.cinematic.client;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.ViewportEvent;
import org.jetbrains.annotations.Nullable;

import java.util.Optional;

/** Client-only camera, overlay, and completion presentation for one server sequence id. */
public interface ClientCinematicPresentation {
    ResourceLocation sequenceId();

    boolean recognizesStage(ResourceLocation stageId);

    void applyCamera(CinematicClientController state, ViewportEvent.ComputeCameraAngles event);

    /** Optional detached world-space camera position for the active stage. */
    default Optional<Vec3> cameraPosition(CinematicClientController state, float partialTick) {
        return Optional.empty();
    }

    /** Allows a presentation to adjust FOV while honoring the player's accessibility scale. */
    default void applyFov(CinematicClientController state, ViewportEvent.ComputeFov event) {
    }

    /** Whether the active stage needs first-person render semantics. */
    default boolean forcesFirstPerson(CinematicClientController state) {
        return state.controlsLocked();
    }

    void renderOverlay(
            CinematicClientController state,
            GuiGraphics graphics,
            int width,
            int height,
            float partialTick
    );

    /** Resolves a server-authored cue id to local translated narration text. */
    default Optional<Component> narration(ResourceLocation cueId) {
        return Optional.empty();
    }

    default void onStarted(CinematicClientController state) {
    }

    default void onStageChanged(CinematicClientController state) {
    }

    default void onNarration(CinematicClientController state, ResourceLocation cueId, Component text) {
    }

    default void onStopped(CinematicClientController state) {
    }

    /** Optional objective or handoff text shown after normal sequence completion. */
    default @Nullable Component completionMessage() {
        return null;
    }

    default int completionMessageTicks() {
        return 100;
    }
}
