package com.thunder.wildernessodysseyapi.Core;


import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.NetworkRegistry;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.simple.SimpleChannel;

/**
 * Handles registration of network messages for Wilderness Odyssey API.
 */
public class WildernessOdysseyAPINetworkHandler {
    private static final String PROTOCOL_VERSION = "1";
    private static final SimpleChannel CHANNEL = NetworkRegistry.ChannelBuilder
            .named(new ResourceLocation(ModConstants.MOD_ID, "main"))
            .networkProtocolVersion(() -> PROTOCOL_VERSION)
            .clientAcceptedVersions(PROTOCOL_VERSION::equals)
            .serverAcceptedVersions(PROTOCOL_VERSION::equals)
            .simpleChannel();

    private static int index = 0;

    private static int nextId() {
        return index++;
    }

    /**
     * Registers all network messages for this mod.
     */
    public static void register() {
        CHANNEL.messageBuilder(PingMessage.class, nextId())
                .encoder(PingMessage::encode)
                .decoder(PingMessage::decode)
                .consumerMainThread(PingMessage::handle)
                .add();
    }

    /**
     * Sends a ping message to the server.
     */
    public static void sendPingToServer() {
        CHANNEL.send(PacketDistributor.SERVER.noArg(), new PingMessage());
    }
}
