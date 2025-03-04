package com.thunder.wildernessodysseyapi.NovaAPI.network;

import net.minecraft.network.FriendlyByteBuf;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;

import static com.thunder.wildernessodysseyapi.MainModClass.WildernessOdysseyAPIMainModClass.MOD_ID;

@EventBusSubscriber(modid = MOD_ID)
public class PacketInterceptor {
    @SubscribeEvent
    public static void onPacketReceived(ClientCustomPayloadEvent event) {
        FriendlyByteBuf buffer = event.getPayload();

        if (!PacketValidator.validatePacket(buffer)) {
            System.err.println("[NovaAPI] Invalid packet detected: " + event.getChannel().toString());
            event.setCanceled(true);
            return;
        }
        System.out.println("[NovaAPI] Packet received successfully: " + event.getChannel().toString());
    }
}