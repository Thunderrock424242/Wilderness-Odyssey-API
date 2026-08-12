package com.thunder.wildernessodysseyapi.developmentstudio.network;

import com.thunder.wildernessodysseyapi.core.ModConstants;
import com.thunder.wildernessodysseyapi.developmentstudio.StudioText;
import com.thunder.wildernessodysseyapi.developmentstudio.bookmark.StudioBookmark;
import com.thunder.wildernessodysseyapi.developmentstudio.inspection.StudioInspection;
import com.thunder.wildernessodysseyapi.developmentstudio.inspection.StudioInspectionLine;
import io.netty.handler.codec.DecoderException;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Server-authorized Studio screen snapshot sent only after the access policy passes.
 *
 * @param initialModule module path to select when opening
 * @param developmentStudioWorld whether the persistent world flag is active
 * @param worldSeed active world's seed
 * @param campusOrigin persisted template origin, or {@code null}
 * @param bookmarks server-owned world bookmark catalog
 * @param inspection optional server-produced inspector result
 */
public record OpenStudioPayload(
        String initialModule,
        boolean developmentStudioWorld,
        long worldSeed,
        BlockPos campusOrigin,
        List<StudioBookmark> bookmarks,
        StudioInspection inspection
) implements CustomPacketPayload {
    private static final int MAX_MODULE_PATH = 32;
    private static final int MAX_BOOKMARKS_ON_WIRE = 512;
    private static final int MAX_INSPECTION_LINES = 64;

    public static final Type<OpenStudioPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(ModConstants.MOD_ID, "open_studio")
    );
    public static final StreamCodec<FriendlyByteBuf, OpenStudioPayload> STREAM_CODEC = StreamCodec.of(
            OpenStudioPayload::write,
            OpenStudioPayload::read
    );

    public OpenStudioPayload {
        initialModule = StudioText.singleLine(initialModule, MAX_MODULE_PATH);
        if (initialModule.isBlank()) {
            initialModule = "world";
        }
        campusOrigin = campusOrigin == null ? null : campusOrigin.immutable();
        bookmarks = bookmarks == null
                ? List.of()
                : List.copyOf(bookmarks.subList(0, Math.min(bookmarks.size(), MAX_BOOKMARKS_ON_WIRE)));
    }

    private static void write(FriendlyByteBuf buffer, OpenStudioPayload payload) {
        buffer.writeUtf(payload.initialModule, MAX_MODULE_PATH);
        buffer.writeBoolean(payload.developmentStudioWorld);
        buffer.writeLong(payload.worldSeed);
        buffer.writeBoolean(payload.campusOrigin != null);
        if (payload.campusOrigin != null) {
            buffer.writeBlockPos(payload.campusOrigin);
        }

        buffer.writeVarInt(payload.bookmarks.size());
        payload.bookmarks.forEach(bookmark -> writeBookmark(buffer, bookmark));
        buffer.writeBoolean(payload.inspection != null);
        if (payload.inspection != null) {
            writeInspection(buffer, payload.inspection);
        }
    }

    private static OpenStudioPayload read(FriendlyByteBuf buffer) {
        String initialModule = buffer.readUtf(MAX_MODULE_PATH);
        boolean developmentStudioWorld = buffer.readBoolean();
        long seed = buffer.readLong();
        BlockPos campusOrigin = buffer.readBoolean() ? buffer.readBlockPos() : null;

        int bookmarkCount = checkedCount(buffer.readVarInt(), MAX_BOOKMARKS_ON_WIRE, "bookmarks");
        List<StudioBookmark> bookmarks = new ArrayList<>(bookmarkCount);
        for (int index = 0; index < bookmarkCount; index++) {
            bookmarks.add(readBookmark(buffer));
        }
        StudioInspection inspection = buffer.readBoolean() ? readInspection(buffer) : null;
        return new OpenStudioPayload(initialModule, developmentStudioWorld, seed, campusOrigin, bookmarks, inspection);
    }

    private static void writeBookmark(FriendlyByteBuf buffer, StudioBookmark bookmark) {
        buffer.writeUUID(bookmark.id());
        buffer.writeUtf(bookmark.name(), StudioText.MAX_BOOKMARK_NAME);
        buffer.writeResourceLocation(bookmark.dimension());
        buffer.writeBlockPos(bookmark.position());
        buffer.writeFloat(bookmark.yaw());
        buffer.writeFloat(bookmark.pitch());
        buffer.writeResourceLocation(bookmark.biome());
        buffer.writeUtf(bookmark.notes(), StudioText.MAX_BOOKMARK_NOTES);
        buffer.writeVarInt(bookmark.tags().size());
        bookmark.tags().forEach(tag -> buffer.writeUtf(tag, StudioText.MAX_TAG_LENGTH));
        buffer.writeLong(bookmark.createdAtEpochMillis());
    }

    private static StudioBookmark readBookmark(FriendlyByteBuf buffer) {
        UUID id = buffer.readUUID();
        String name = buffer.readUtf(StudioText.MAX_BOOKMARK_NAME);
        ResourceLocation dimension = buffer.readResourceLocation();
        BlockPos position = buffer.readBlockPos();
        float yaw = buffer.readFloat();
        float pitch = buffer.readFloat();
        ResourceLocation biome = buffer.readResourceLocation();
        String notes = buffer.readUtf(StudioText.MAX_BOOKMARK_NOTES);
        int tagCount = checkedCount(buffer.readVarInt(), StudioText.MAX_TAGS, "bookmark tags");
        List<String> tags = new ArrayList<>(tagCount);
        for (int index = 0; index < tagCount; index++) {
            tags.add(buffer.readUtf(StudioText.MAX_TAG_LENGTH));
        }
        return new StudioBookmark(
                id, name, dimension, position, yaw, pitch, biome, notes, tags, buffer.readLong()
        );
    }

    private static void writeInspection(FriendlyByteBuf buffer, StudioInspection inspection) {
        buffer.writeResourceLocation(inspection.providerId());
        buffer.writeUtf(inspection.title(), 96);
        buffer.writeVarInt(Math.min(inspection.lines().size(), MAX_INSPECTION_LINES));
        inspection.lines().stream().limit(MAX_INSPECTION_LINES).forEach(line -> {
            buffer.writeUtf(line.label(), 48);
            buffer.writeUtf(line.value(), 256);
        });
    }

    private static StudioInspection readInspection(FriendlyByteBuf buffer) {
        ResourceLocation providerId = buffer.readResourceLocation();
        String title = buffer.readUtf(96);
        int lineCount = checkedCount(buffer.readVarInt(), MAX_INSPECTION_LINES, "inspection lines");
        List<StudioInspectionLine> lines = new ArrayList<>(lineCount);
        for (int index = 0; index < lineCount; index++) {
            lines.add(new StudioInspectionLine(buffer.readUtf(48), buffer.readUtf(256)));
        }
        return new StudioInspection(providerId, title, lines);
    }

    private static int checkedCount(int count, int maximum, String field) {
        if (count < 0 || count > maximum) {
            throw new DecoderException("Invalid Studio " + field + " count: " + count);
        }
        return count;
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
