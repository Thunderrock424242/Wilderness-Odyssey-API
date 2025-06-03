package com.thunder.wildernessodysseyapi.Core;

import com.thunder.wildernessodysseyapi.WorldVersionChecker.Packet.SyncWorldVersionPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.NetworkRegistry;



public class NovaAPINetworkHandler {

    public static final ResourceLocation SYNC_WORLD_VERSION_ID =
            new ResourceLocation(ModConstants.MOD_ID, "sync_world_version");

    @SubscribeEvent
    public static void register(final RegisterPayloadHandlersEvent event) {
        event.registrar(ModConstants.MOD_ID).registerClientbound(
                SYNC_WORLD_VERSION_ID,
                SyncWorldVersionPacket::decode,
                SyncWorldVersionPacket::encode,
                SyncWorldVersionPacket::handle
        );
    }

    public static void sendTo(ServerPlayer player, SyncWorldVersionPacket packet) {
        NetworkRegistry.sendToPlayer(player, SYNC_WORLD_VERSION_ID, packet);
    }
}