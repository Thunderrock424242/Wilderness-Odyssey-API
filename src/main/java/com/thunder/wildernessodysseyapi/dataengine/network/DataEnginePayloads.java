package com.thunder.wildernessodysseyapi.dataengine.network;

import com.thunder.wildernessodysseyapi.dataengine.DataEngine;
import com.thunder.wildernessodysseyapi.dataengine.DataEngineIds;
import com.thunder.wildernessodysseyapi.dataengine.debug.DataEngineDebugSubscriptionPayload;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

/** Registers Data Engine batch transport and its infrequent debug subscription request. */
public final class DataEnginePayloads {
    private DataEnginePayloads() {
    }

    /** Adds Data Engine payloads to the mod's existing versioned play registrar. */
    public static void register(PayloadRegistrar registrar) {
        registrar.playToClient(
                DataPacketBatch.TYPE,
                DataPacketBatch.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() -> DataDeltaHandlerRegistry.dispatch(payload))
        );
        registrar.playToServer(
                DataEngineDebugSubscriptionPayload.TYPE,
                DataEngineDebugSubscriptionPayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() -> {
                    if (!(context.player() instanceof ServerPlayer player)) {
                        return;
                    }
                    DataEngine engine = DataEngine.get();
                    if (!engine.isRunning()) {
                        return;
                    }
                    boolean allowed = payload.subscribed()
                            && player.createCommandSourceStack().hasPermission(2);
                    engine.setFeatureInterest(player, DataEngineIds.DEBUG_METRICS, allowed);
                })
        );
    }
}
