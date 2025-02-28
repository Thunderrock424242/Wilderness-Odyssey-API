package com.thunder.wildernessodysseyapi.NovaAPI.network;

import net.minecraft.network.FriendlyByteBuf;

public class PacketValidator {
    public static boolean validatePacket(FriendlyByteBuf buffer) {
        try {
            if (buffer.readableBytes() > 4096) {
                System.err.println("[NovaAPI] Packet too large! Possible exploit attempt.");
                return false;
            }
            buffer.markReaderIndex();
            while (buffer.isReadable()) {
                buffer.readByte();
            }
            buffer.resetReaderIndex();
            return true;
        } catch (Exception e) {
            System.err.println("[NovaAPI] Malformed packet detected: " + e.getMessage());
            return false;
        }
    }
}