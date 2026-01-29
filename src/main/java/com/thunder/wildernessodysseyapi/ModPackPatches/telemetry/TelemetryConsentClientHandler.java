package com.thunder.wildernessodysseyapi.ModPackPatches.telemetry;

import com.thunder.wildernessodysseyapi.Core.ModConstants;
import com.thunder.wildernessodysseyapi.ModPackPatches.telemetry.TelemetryConsentStore.ConsentDecision;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.TitleScreen;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;

/**
 * Displays telemetry consent UI on client login when needed.
 */
@EventBusSubscriber(modid = ModConstants.MOD_ID, value = Dist.CLIENT)
public final class TelemetryConsentClientHandler {
    private static boolean promptedThisSession = false;

    private TelemetryConsentClientHandler() {
    }

    @SubscribeEvent
    public static void onClientLogin(ClientPlayerNetworkEvent.LoggingIn event) {
        TelemetryConsentConfig.validateVersion();
        ConsentDecision decision = TelemetryConsentConfig.decision();
        Minecraft minecraft = Minecraft.getInstance();
        minecraft.execute(() -> {
            if (decision == ConsentDecision.UNKNOWN) {
                if (!promptedThisSession && minecraft.level != null) {
                    promptedThisSession = true;
                    minecraft.setScreen(new TelemetryConsentScreen(minecraft.screen));
                }
            } else {
                syncDecisionIfPossible(decision);
            }
        });
    }

    static void syncDecisionIfPossible(ConsentDecision decision) {
        if (decision == ConsentDecision.UNKNOWN) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.player.connection == null) {
            return;
        }
        String command = decision == ConsentDecision.ACCEPTED ? "telemetryconsent accept" : "telemetryconsent decline";
        minecraft.player.connection.sendCommand(command);
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        if (promptedThisSession) {
            return;
        }
        TelemetryConsentConfig.validateVersion();
        if (TelemetryConsentConfig.decision() != ConsentDecision.UNKNOWN) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.screen instanceof TitleScreen) {
            promptedThisSession = true;
            minecraft.setScreen(new TelemetryConsentScreen(minecraft.screen));
        }
    }
}
