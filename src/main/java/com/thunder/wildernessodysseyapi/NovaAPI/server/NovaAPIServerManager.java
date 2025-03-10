package com.thunder.wildernessodysseyapi.NovaAPI.server;

import com.thunder.wildernessodysseyapi.NovaAPI.config.NovaAPIConfig;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;

public class NovaAPIServerManager {
    private static boolean isDedicatedMode = false;
    private static boolean isServerRunning = false;

    public static void initialize(FMLCommonSetupEvent event) {
        if (isServerRunning) {
            NovaAPI.LOGGER.warn("[Nova API] Another instance is already running. Preventing conflict.");
            return;
        }

        if (NovaAPIConfig.ENABLE_DEDICATED_SERVER.get()) {
            isDedicatedMode = true;
            String serverIP = NovaAPIConfig.DEDICATED_SERVER_IP.get();
            connectToDedicatedServer(serverIP);
        } else {
            startLocalServer();
        }

        isServerRunning = true;
    }

    private static void startLocalServer() {
        NovaAPI.LOGGER.info("[Nova API] Starting in Local Mode...");
        // TODO: Initialize Local Mode optimizations (chunk preloading, AI, etc.)
    }

    private static void connectToDedicatedServer(String serverIP) {
        NovaAPI.LOGGER.info("[Nova API] Connecting to Dedicated Nova API Server at " + serverIP + "...");
        // TODO: Implement secure connection and authentication
    }

    public static boolean isDedicatedMode() {
        return isDedicatedMode;
    }
}