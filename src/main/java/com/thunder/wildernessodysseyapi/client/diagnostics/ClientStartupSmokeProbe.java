package com.thunder.wildernessodysseyapi.client.diagnostics;

import com.thunder.wildernessodysseyapi.client.notice.AlphaNoticeScreen;
import com.thunder.wildernessodysseyapi.core.ModConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ScreenEvent;

import java.util.concurrent.TimeUnit;

/**
 * Opt-in CI evidence that a supported startup screen rendered after loading.
 * Runs only on the physical client; normal launches never request an automatic exit.
 */
@EventBusSubscriber(modid = ModConstants.MOD_ID, value = Dist.CLIENT)
public final class ClientStartupSmokeProbe {
    private static final boolean ENABLED = Boolean.getBoolean("wildernessodysseyapi.ciSmokeTest");
    private static final long SOAK_NANOS = TimeUnit.SECONDS.toNanos(60);
    private static long firstRenderNanos;
    private static boolean stopRequested;

    private ClientStartupSmokeProbe() {
    }

    /** Uses a completed screen render rather than an incidental resource-atlas log message. */
    @SubscribeEvent
    public static void afterScreenRender(ScreenEvent.Render.Post event) {
        if (!ENABLED || stopRequested) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        Screen screen = event.getScreen();
        if (screen != minecraft.screen || minecraft.level != null || minecraft.getOverlay() != null
                || !(screen instanceof TitleScreen || screen instanceof AlphaNoticeScreen)) {
            firstRenderNanos = 0;
            return;
        }
        long now = System.nanoTime();
        if (firstRenderNanos == 0) {
            firstRenderNanos = now;
            ModConstants.LOGGER.info("[WO-CI] CLIENT_READY screen={} class={}",
                    screen instanceof AlphaNoticeScreen ? "AlphaNoticeScreen" : "TitleScreen",
                    screen.getClass().getName());
        } else if (now - firstRenderNanos >= SOAK_NANOS) {
            stopRequested = true;
            ModConstants.LOGGER.info("[WO-CI] CLIENT_STOP_REQUESTED");
            minecraft.stop();
        }
    }
}
