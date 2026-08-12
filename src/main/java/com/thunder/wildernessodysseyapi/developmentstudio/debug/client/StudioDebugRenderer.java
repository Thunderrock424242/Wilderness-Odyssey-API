package com.thunder.wildernessodysseyapi.developmentstudio.debug.client;

import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;

/** One optional, independently toggled Development Studio world overlay. */
public interface StudioDebugRenderer {
    ResourceLocation id();

    RenderLevelStageEvent.Stage stage();

    /** Draws only after the registry has confirmed this renderer is enabled. */
    void render(Minecraft minecraft, RenderLevelStageEvent event);
}
