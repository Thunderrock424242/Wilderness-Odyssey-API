package com.thunder.wildernessodysseyapi.lorebook.network;

import com.thunder.wildernessodysseyapi.core.ModConstants;
import com.thunder.wildernessodysseyapi.lorebook.map.CodexMapPoi;
import com.thunder.wildernessodysseyapi.lorebook.map.CodexMapSettings;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.List;

/**
 * Synchronizes server map settings and known Wilderness Odyssey POIs before the
 * client opens the Field Codex.
 *
 * <p>The server remains authoritative for POI discovery; the client only draws
 * what this payload provides.</p>
 */
public record SyncCodexMapPayload(CodexMapSettings settings, List<CodexMapPoi> pois) implements CustomPacketPayload {
    private static final int MAX_POIS = 512;

    public static final Type<SyncCodexMapPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(ModConstants.MOD_ID, "sync_codex_map"));

    public static final StreamCodec<FriendlyByteBuf, SyncCodexMapPayload> STREAM_CODEC =
            StreamCodec.of(SyncCodexMapPayload::encode, SyncCodexMapPayload::decode);

    public SyncCodexMapPayload {
        pois = List.copyOf(pois);
    }

    private static void encode(FriendlyByteBuf buffer, SyncCodexMapPayload payload) {
        payload.settings.encode(buffer);
        int count = Math.min(payload.pois.size(), MAX_POIS);
        buffer.writeVarInt(count);
        for (int i = 0; i < count; i++) {
            payload.pois.get(i).encode(buffer);
        }
    }

    private static SyncCodexMapPayload decode(FriendlyByteBuf buffer) {
        CodexMapSettings settings = CodexMapSettings.decode(buffer);
        int count = Math.min(buffer.readVarInt(), MAX_POIS);
        List<CodexMapPoi> pois = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            pois.add(CodexMapPoi.decode(buffer));
        }
        return new SyncCodexMapPayload(settings, pois);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
