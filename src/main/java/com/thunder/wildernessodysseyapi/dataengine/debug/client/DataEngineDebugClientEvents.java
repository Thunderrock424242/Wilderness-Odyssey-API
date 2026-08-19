package com.thunder.wildernessodysseyapi.dataengine.debug.client;

import com.thunder.wildernessodysseyapi.core.ModConstants;
import com.thunder.wildernessodysseyapi.dataengine.DataEngineIds;
import com.thunder.wildernessodysseyapi.dataengine.debug.DataEngineDebugSnapshotCodec;
import com.thunder.wildernessodysseyapi.dataengine.debug.DataEngineDebugSubscriptionPayload;
import com.thunder.wildernessodysseyapi.dataengine.network.DataDeltaHandlerRegistry;
import com.thunder.wildernessodysseyapi.debugoverlay.client.WildernessDebugManager;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

/** Client-only page subscription and delta decode bridge. */
@EventBusSubscriber(modid = ModConstants.MOD_ID, value = Dist.CLIENT)
public final class DataEngineDebugClientEvents {
    private static boolean handlerRegistered;
    private static boolean subscribed;

    private DataEngineDebugClientEvents() {
    }

    /** Sends only page-open/page-close transitions; no per-tick request packet is generated. */
    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        ensureHandlerRegistered();
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.getConnection() == null || minecraft.player == null) {
            subscribed = false;
            DataEngineDebugClientState.clear();
            return;
        }

        boolean shouldSubscribe = minecraft.getDebugOverlay().showDebugScreen()
                && minecraft.screen == null
                && !minecraft.showOnlyReducedInfo()
                && DataEngineIds.DEBUG_METRICS.equals(WildernessDebugManager.get().selectedPageId());
        if (shouldSubscribe == subscribed) {
            return;
        }
        subscribed = shouldSubscribe;
        PacketDistributor.sendToServer(new DataEngineDebugSubscriptionPayload(subscribed));
        if (!subscribed) {
            DataEngineDebugClientState.clear();
        }
    }

    private static void ensureHandlerRegistered() {
        if (handlerRegistered) {
            return;
        }
        handlerRegistered = true;
        DataDeltaHandlerRegistry.register(DataEngineIds.DEBUG_METRICS, delta -> {
            if (delta.targetKey() == 0L && delta.changedFields() == 1L) {
                DataEngineDebugClientState.accept(DataEngineDebugSnapshotCodec.decode(delta.body()));
            }
        });
    }
}
