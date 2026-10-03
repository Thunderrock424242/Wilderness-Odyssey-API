package com.thunder.wildernessodysseyapi.quest.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;

/** Shared bounded wire primitives. Lengths are checked before allocating arrays. */
public final class QuestWire {
    public static final int CHUNK_BYTES = 32 * 1024;
    public static final int MAX_VIEW_BYTES = 16 * 1024 * 1024;
    private QuestWire() { }
    static byte[] readChunk(FriendlyByteBuf buffer) {
        int length = buffer.readVarInt();
        if (length < 0 || length > CHUNK_BYTES || length > buffer.readableBytes()) throw new IllegalArgumentException("Invalid quest chunk length.");
        byte[] bytes = new byte[length]; buffer.readBytes(bytes); return bytes;
    }
    static void writeChunk(FriendlyByteBuf buffer, byte[] bytes) { buffer.writeVarInt(bytes.length); buffer.writeBytes(bytes); }
    public static void hash(String hash, boolean emptyAllowed) {
        if (hash == null || !(emptyAllowed && hash.isEmpty()) && !hash.matches("[0-9a-f]{64}")) throw new IllegalArgumentException("Invalid quest publication hash.");
    }
    static void id(String value, boolean emptyAllowed) {
        if (value == null || value.length() > 256 || !(emptyAllowed && value.isEmpty())
                && (!value.contains(":") || ResourceLocation.tryParse(value) == null)) throw new IllegalArgumentException("Invalid quest identifier.");
    }
}
