package com.thunder.wildernessodysseyapi.Core;

import com.thunder.wildernessodysseyapi.WorldVersionChecker.Packet.SyncWorldVersionPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.NetworkRegistry;

import static com.thunder.wildernessodysseyapi.Core.ModConstants.MOD_ID;


public class NovaAPINetworkHandler {

    // (1) Use ResourceLocation.of(namespace, path)
    public static final ResourceLocation SYNC_WORLD_VERSION_ID =
            ResourceLocation.tryParse(MOD_ID);

    // (2) Register clientbound handler directly on the event
    @SubscribeEvent
    public static void register(RegisterPayloadHandlersEvent event) {
        event.registrar(MOD_ID).registerClientbound(
                SYNC_WORLD_VERSION_ID,
                SyncWorldVersionPacket::decode,
                SyncWorldVersionPacket::encode,
                SyncWorldVersionPacket::handle
        );
    }

    // (3) Use NetworkRegistry.sendToClient(...) to send a clientbound packet
    public static void sendTo(ServerPlayer player, SyncWorldVersionPacket packet) {
        NetworkRegistry.sendToPlayer(player, SYNC_WORLD_VERSION_ID, packet);
    }
}