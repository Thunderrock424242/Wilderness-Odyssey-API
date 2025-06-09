package com.thunder.wildernessodysseyapi.Core;

import com.thunder.wildernessodysseyapi.WorldVersionChecker.Packet.SyncWorldVersionPacket;
import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.protocol.common.ClientboundCustomPayloadPacket;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

import static com.thunder.wildernessodysseyapi.Core.ModConstants.MOD_ID;


public class WildernessOdysseyAPINetworkHandler {

    // (1) Use ResourceLocation.of(namespace, path)
    public static final ResourceLocation SYNC_WORLD_VERSION_ID =
            ResourceLocation.tryParse(MOD_ID);

    public static void sendTo(ServerPlayer player, SyncWorldVersionPacket packet) {
        // 1) Write packet data into a FriendlyByteBuf
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        packet.encode(buf);

        // 2) Construct the CustomPacketPayload record with our ID + buffer
        CustomPacketPayload payload = new CustomPacketPayload() {
            /**
             * @return
             */
            @Override
            public Type<? extends CustomPacketPayload> type() {
                return null;
            }
        };

        // 3) Send via the ClientboundCustomPayloadPacket constructor that takes a CustomPacketPayload
        player.connection.send(new ClientboundCustomPayloadPacket(payload));
    }

}