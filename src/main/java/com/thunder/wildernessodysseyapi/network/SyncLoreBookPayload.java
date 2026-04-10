package com.thunder.wildernessodysseyapi.network;

import com.thunder.wildernessodysseyapi.core.ModConstants;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record SyncLoreBookPayload(String bookId) implements CustomPacketPayload {
    public static final Type<SyncLoreBookPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(ModConstants.MOD_ID, "sync_lore_book"));

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}