package com.thunder.wildernessodysseyapi.lorebook.network;

import com.thunder.wildernessodysseyapi.core.ModConstants;
import com.thunder.wildernessodysseyapi.lorebook.CodexJournalText;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Synchronizes the server-owned personal journal before the Codex screen opens.
 *
 * @param text complete sanitized journal text for the receiving player
 */
public record SyncCodexJournalPayload(String text) implements CustomPacketPayload {
    public static final Type<SyncCodexJournalPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(ModConstants.MOD_ID, "sync_codex_journal")
    );
    public static final StreamCodec<FriendlyByteBuf, SyncCodexJournalPayload> STREAM_CODEC = StreamCodec.of(
            (buffer, payload) -> buffer.writeUtf(payload.text(), CodexJournalText.MAX_LENGTH),
            buffer -> new SyncCodexJournalPayload(buffer.readUtf(CodexJournalText.MAX_LENGTH))
    );

    public SyncCodexJournalPayload {
        text = CodexJournalText.sanitize(text);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
