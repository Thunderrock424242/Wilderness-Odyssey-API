package com.thunder.wildernessodysseyapi.lorebook.network;

import com.thunder.wildernessodysseyapi.core.ModConstants;
import com.thunder.wildernessodysseyapi.lorebook.CodexJournalText;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Carries personal journal edits from the Codex screen to the authoritative server.
 *
 * @param text complete sanitized journal text to persist for the sending player
 */
public record SaveCodexJournalPayload(String text) implements CustomPacketPayload {
    public static final Type<SaveCodexJournalPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(ModConstants.MOD_ID, "save_codex_journal")
    );
    public static final StreamCodec<FriendlyByteBuf, SaveCodexJournalPayload> STREAM_CODEC = StreamCodec.of(
            (buffer, payload) -> buffer.writeUtf(payload.text(), CodexJournalText.MAX_LENGTH),
            buffer -> new SaveCodexJournalPayload(buffer.readUtf(CodexJournalText.MAX_LENGTH))
    );

    public SaveCodexJournalPayload {
        text = CodexJournalText.sanitize(text);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
