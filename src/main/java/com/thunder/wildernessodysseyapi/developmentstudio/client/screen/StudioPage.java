package com.thunder.wildernessodysseyapi.developmentstudio.client.screen;

import net.minecraft.client.gui.GuiGraphics;

/** Modular content page hosted by {@link StudioScreen}. */
public interface StudioPage {
    /** Creates page-specific widgets inside the screen's content region. */
    default void init(StudioScreen screen) {
    }

    /** Draws page content before interactive widgets render. */
    void render(StudioScreen screen, GuiGraphics graphics, int mouseX, int mouseY, float partialTick);
}
