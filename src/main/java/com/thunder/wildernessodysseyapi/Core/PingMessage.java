package com.thunder.wildernessodysseyapi.Core;

import net.minecraft.network.FriendlyByteBuf;
import net.neoforged.neoforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * Simple ping message for testing network connectivity.
 */
public class PingMessage {
    public PingMessage() {
    }

    public static PingMessage decode(FriendlyByteBuf buf) {
        return new PingMessage();
    }

    public void encode(FriendlyByteBuf buf) {
        // no payload
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> ModConstants.LOGGER.info("Received ping message"));
        ctx.get().setPacketHandled(true);
    }
}
