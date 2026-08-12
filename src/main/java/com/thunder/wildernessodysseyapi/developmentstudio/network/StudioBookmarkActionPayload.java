package com.thunder.wildernessodysseyapi.developmentstudio.network;

import com.thunder.wildernessodysseyapi.core.ModConstants;
import com.thunder.wildernessodysseyapi.developmentstudio.StudioText;
import io.netty.handler.codec.DecoderException;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Bounded bookmark mutation request; all position and dimension facts remain server-owned.
 *
 * @param action requested high-level operation
 * @param bookmarkId existing id, or zero UUID when creating
 * @param name bounded requested display name
 * @param notes bounded requested notes
 * @param tags bounded requested tags
 */
public record StudioBookmarkActionPayload(
        Action action,
        UUID bookmarkId,
        String name,
        String notes,
        List<String> tags
) implements CustomPacketPayload {
    private static final UUID ZERO_UUID = new UUID(0L, 0L);

    public static final Type<StudioBookmarkActionPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(ModConstants.MOD_ID, "studio_bookmark_action")
    );
    public static final StreamCodec<FriendlyByteBuf, StudioBookmarkActionPayload> STREAM_CODEC = StreamCodec.of(
            StudioBookmarkActionPayload::write,
            StudioBookmarkActionPayload::read
    );

    public StudioBookmarkActionPayload {
        action = action == null ? Action.CREATE : action;
        bookmarkId = bookmarkId == null ? ZERO_UUID : bookmarkId;
        name = StudioText.singleLine(name, StudioText.MAX_BOOKMARK_NAME);
        notes = StudioText.notes(notes);
        tags = StudioText.tags(tags);
    }

    public static StudioBookmarkActionPayload create(String name, String notes, List<String> tags) {
        return new StudioBookmarkActionPayload(Action.CREATE, ZERO_UUID, name, notes, tags);
    }

    private static void write(FriendlyByteBuf buffer, StudioBookmarkActionPayload payload) {
        buffer.writeEnum(payload.action);
        buffer.writeUUID(payload.bookmarkId);
        buffer.writeUtf(payload.name, StudioText.MAX_BOOKMARK_NAME);
        buffer.writeUtf(payload.notes, StudioText.MAX_BOOKMARK_NOTES);
        buffer.writeVarInt(payload.tags.size());
        payload.tags.forEach(tag -> buffer.writeUtf(tag, StudioText.MAX_TAG_LENGTH));
    }

    private static StudioBookmarkActionPayload read(FriendlyByteBuf buffer) {
        Action action = buffer.readEnum(Action.class);
        UUID id = buffer.readUUID();
        String name = buffer.readUtf(StudioText.MAX_BOOKMARK_NAME);
        String notes = buffer.readUtf(StudioText.MAX_BOOKMARK_NOTES);
        int count = buffer.readVarInt();
        if (count < 0 || count > StudioText.MAX_TAGS) {
            throw new DecoderException("Invalid Studio bookmark tag count: " + count);
        }
        List<String> tags = new ArrayList<>(count);
        for (int index = 0; index < count; index++) {
            tags.add(buffer.readUtf(StudioText.MAX_TAG_LENGTH));
        }
        return new StudioBookmarkActionPayload(action, id, name, notes, tags);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public enum Action {
        CREATE,
        UPDATE,
        DELETE,
        TELEPORT
    }
}
