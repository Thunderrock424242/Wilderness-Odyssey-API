package com.thunder.wildernessodysseyapi.Core;

import com.thunder.wildernessodysseyapi.WorldVersionChecker.Packet.SyncWorldVersionPacket;
import net.minecraft.resources.ResourceLocation;

public class NovaAPINetworkHandler {
    private static final String PROTOCOL = "1";
    public static SimpleChannel CHANNEL;

    public static void init() {
        CHANNEL = NetworkRegistry.newSimpleChannel(
                new ResourceLocation("novaapi", "main"),
                () -> PROTOCOL,
                PROTOCOL::equals,
                PROTOCOL::equals
        );

        int id = 0;
        CHANNEL.registerMessage(id++, SyncWorldVersionPacket.class,
                SyncWorldVersionPacket::encode,
                SyncWorldVersionPacket::decode,
                SyncWorldVersionPacket::handle
        );
    }
}
