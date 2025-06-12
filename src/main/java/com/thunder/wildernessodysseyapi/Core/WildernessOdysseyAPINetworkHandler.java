package com.thunder.wildernessodysseyapi.Core;

import com.thunder.wildernessodysseyapi.WorldVersionChecker.Packet.SyncWorldVersionPacket;
import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.protocol.common.ClientboundCustomPayloadPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.registration.NetworkRegistry;

import static com.thunder.wildernessodysseyapi.Core.ModConstants.MOD_ID;


public class WildernessOdysseyAPINetworkHandler {
    public static final ResourceLocation SYNC_WORLD_VERSION_ID =
            ResourceLocation.tryBuild(MOD_ID, "sync_world_version");

    /**
     * Call once at mod init (e.g., in your @Mod constructor) to activate
     * NeoForge networking negotiation for custom payloads.
     */
    public static void init() {
        NetworkRegistry.setup();
    }

    /**
     * Send the sync packet to a specific player.
     */
    public static void sendTo(ServerPlayer player, SyncWorldVersionPacket packet) {
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        packet.encode(buf);
        // Directly wrap id and buf without CustomPacketPayload
        player.connection.send(new ClientboundCustomPayloadPacket(SYNC_WORLD_VERSION_ID, buf));
    }
}

