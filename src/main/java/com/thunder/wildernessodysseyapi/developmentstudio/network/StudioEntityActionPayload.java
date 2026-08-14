package com.thunder.wildernessodysseyapi.developmentstudio.network;

import com.thunder.wildernessodysseyapi.core.ModConstants;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** High-level Entity Lab request with a fixed allowlisted type and bounded count. */
public record StudioEntityActionPayload(
        Action action,
        ResourceLocation entityTypeId,
        int count
) implements CustomPacketPayload {
    public static final int MAX_REQUEST_COUNT = 10;
    public static final Type<StudioEntityActionPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(ModConstants.MOD_ID, "studio_entity_action")
    );
    public static final StreamCodec<FriendlyByteBuf, StudioEntityActionPayload> STREAM_CODEC = StreamCodec.of(
            StudioEntityActionPayload::write,
            StudioEntityActionPayload::read
    );

    public StudioEntityActionPayload {
        action = action == null ? Action.SPAWN : action;
        entityTypeId = entityTypeId == null
                ? ResourceLocation.withDefaultNamespace("cow")
                : entityTypeId;
        count = Math.max(1, Math.min(count, MAX_REQUEST_COUNT));
    }

    private static void write(FriendlyByteBuf buffer, StudioEntityActionPayload payload) {
        buffer.writeEnum(payload.action);
        buffer.writeResourceLocation(payload.entityTypeId);
        buffer.writeVarInt(payload.count);
    }

    private static StudioEntityActionPayload read(FriendlyByteBuf buffer) {
        return new StudioEntityActionPayload(
                buffer.readEnum(Action.class),
                buffer.readResourceLocation(),
                buffer.readVarInt()
        );
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public enum Action {
        SPAWN,
        CLEAR,
        FREEZE,
        UNFREEZE,
        MAKE_INVULNERABLE,
        MAKE_VULNERABLE
    }
}
