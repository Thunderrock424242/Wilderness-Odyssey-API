package com.thunder.wildernessodysseyapi.loading.client;

import com.thunder.wildernessodysseyapi.loading.WorldLoadCancellation;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.DisconnectedScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.network.chat.Component;

/** Client-thread teardown and UI; never invoked by the diagnostic worker. */
public final class WorldLoadFailureHandler {
    private static IntegratedServer retiringServer;
    private static boolean failing;
    private static boolean startingServer;

    private WorldLoadFailureHandler() {
    }

    public static boolean isFailing() {
        return failing;
    }

    public static void setStartingServer(boolean starting) {
        startingServer = starting;
    }

    public static boolean isStartingServer() {
        return startingServer;
    }

    /** Prevent overlapping servers/registry lifecycles while native cleanup still owns the save. */
    public static boolean isWaitingForShutdown() {
        if (retiringServer != null && retiringServer.isShutdown()) {
            retiringServer = null;
        }
        return retiringServer != null;
    }

    public static Screen screen(boolean waiting) {
        Component message = Component.translatable("loading.wildernessodysseyapi.failed.message");
        if (waiting) {
            message = message.copy().append("\n\n").append(Component.translatable("loading.wildernessodysseyapi.failed.waiting"));
        }
        return new DisconnectedScreen(new TitleScreen(), Component.translatable("loading.wildernessodysseyapi.failed.title"),
                message, Component.translatable("gui.toTitle"));
    }

    public static void fail(Minecraft minecraft) {
        if (failing) {
            return;
        }
        failing = true;
        try {
            IntegratedServer server = minecraft.getSingleplayerServer();
            if (server != null) {
                retiringServer = server;
                ((WorldLoadCancellation) server).wildernessodysseyapi$cancelWorldLoad();
            }
            LoadingStallDetector.finishTimedOutLoad();
            if (minecraft.level != null) {
                minecraft.level.disconnect();
            }
            minecraft.disconnect(screen(false));
        } finally {
            failing = false;
        }
    }
}
