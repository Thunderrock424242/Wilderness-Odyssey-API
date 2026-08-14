package com.thunder.wildernessodysseyapi.developmentstudio.network;

import com.thunder.wildernessodysseyapi.core.ModConstants;
import com.thunder.wildernessodysseyapi.developmentstudio.StudioText;
import com.thunder.wildernessodysseyapi.developmentstudio.bookmark.StudioBookmark;
import com.thunder.wildernessodysseyapi.developmentstudio.inspection.StudioInspection;
import com.thunder.wildernessodysseyapi.developmentstudio.inspection.StudioInspectionLine;
import com.thunder.wildernessodysseyapi.developmentstudio.entity.StudioEntityOption;
import com.thunder.wildernessodysseyapi.developmentstudio.region.StudioResetPolicy;
import com.thunder.wildernessodysseyapi.developmentstudio.region.StudioTestRegion;
import com.thunder.wildernessodysseyapi.developmentstudio.region.StudioTestRegionType;
import com.thunder.wildernessodysseyapi.developmentstudio.structure.StudioStructureOption;
import com.thunder.wildernessodysseyapi.developmentstudio.structure.StudioStructurePreview;
import io.netty.handler.codec.DecoderException;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;

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
 * @param testRegions persisted server-owned lab bounds
 * @param structures allowlisted templates available for previews
 * @param entityTypes fixed Entity Lab spawn allowlist
 * @param entityLabEntityCount current in-region Studio-tagged entity count
 * @param structurePreview optional server-computed preview for this player
 * @param inspection optional server-produced inspector result
 */
public record OpenStudioPayload(
        String initialModule,
        boolean developmentStudioWorld,
        long worldSeed,
        BlockPos campusOrigin,
        List<StudioBookmark> bookmarks,
        List<StudioTestRegion> testRegions,
        List<StudioStructureOption> structures,
        List<StudioEntityOption> entityTypes,
        int entityLabEntityCount,
        StudioStructurePreview structurePreview,
        StudioInspection inspection
) implements CustomPacketPayload {
    private static final int MAX_MODULE_PATH = 32;
    private static final int MAX_BOOKMARKS_ON_WIRE = 512;
    private static final int MAX_INSPECTION_LINES = 64;
    private static final int MAX_TEST_REGIONS = 32;
    private static final int MAX_STRUCTURES = 128;
    private static final int MAX_ENTITY_TYPES = 32;

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
        testRegions = boundedCopy(testRegions, MAX_TEST_REGIONS);
        structures = boundedCopy(structures, MAX_STRUCTURES);
        entityTypes = boundedCopy(entityTypes, MAX_ENTITY_TYPES);
        entityLabEntityCount = Math.max(0, entityLabEntityCount);
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
        buffer.writeVarInt(payload.testRegions.size());
        payload.testRegions.forEach(region -> writeRegion(buffer, region));
        buffer.writeVarInt(payload.structures.size());
        payload.structures.forEach(option -> writeStructureOption(buffer, option));
        buffer.writeVarInt(payload.entityTypes.size());
        payload.entityTypes.forEach(option -> {
            buffer.writeResourceLocation(option.id());
            buffer.writeUtf(option.displayName(), 48);
        });
        buffer.writeVarInt(payload.entityLabEntityCount);
        buffer.writeBoolean(payload.structurePreview != null);
        if (payload.structurePreview != null) {
            writeStructurePreview(buffer, payload.structurePreview);
        }
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
        int regionCount = checkedCount(buffer.readVarInt(), MAX_TEST_REGIONS, "test regions");
        List<StudioTestRegion> regions = new ArrayList<>(regionCount);
        for (int index = 0; index < regionCount; index++) {
            regions.add(readRegion(buffer));
        }
        int structureCount = checkedCount(buffer.readVarInt(), MAX_STRUCTURES, "structures");
        List<StudioStructureOption> structures = new ArrayList<>(structureCount);
        for (int index = 0; index < structureCount; index++) {
            structures.add(readStructureOption(buffer));
        }
        int entityTypeCount = checkedCount(buffer.readVarInt(), MAX_ENTITY_TYPES, "entity types");
        List<StudioEntityOption> entityTypes = new ArrayList<>(entityTypeCount);
        for (int index = 0; index < entityTypeCount; index++) {
            entityTypes.add(new StudioEntityOption(buffer.readResourceLocation(), buffer.readUtf(48)));
        }
        int entityLabEntityCount = buffer.readVarInt();
        if (entityLabEntityCount < 0 || entityLabEntityCount > 1_024) {
            throw new DecoderException("Invalid Studio entity count: " + entityLabEntityCount);
        }
        StudioStructurePreview preview = buffer.readBoolean() ? readStructurePreview(buffer) : null;
        StudioInspection inspection = buffer.readBoolean() ? readInspection(buffer) : null;
        return new OpenStudioPayload(
                initialModule, developmentStudioWorld, seed, campusOrigin, bookmarks,
                regions, structures, entityTypes, entityLabEntityCount, preview, inspection
        );
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

    private static void writeRegion(FriendlyByteBuf buffer, StudioTestRegion region) {
        buffer.writeResourceLocation(region.id());
        buffer.writeUtf(region.displayName(), StudioTestRegion.MAX_DISPLAY_NAME);
        buffer.writeResourceLocation(region.dimension());
        buffer.writeBlockPos(region.min());
        buffer.writeBlockPos(region.max());
        buffer.writeEnum(region.type());
        buffer.writeEnum(region.resetPolicy());
    }

    private static StudioTestRegion readRegion(FriendlyByteBuf buffer) {
        StudioTestRegion region = new StudioTestRegion(
                buffer.readResourceLocation(),
                buffer.readUtf(StudioTestRegion.MAX_DISPLAY_NAME),
                buffer.readResourceLocation(),
                buffer.readBlockPos(),
                buffer.readBlockPos(),
                buffer.readEnum(StudioTestRegionType.class),
                buffer.readEnum(StudioResetPolicy.class)
        );
        if (region.volume() > com.thunder.wildernessodysseyapi.developmentstudio.region.StudioTestRegionRegistry.MAX_REGION_VOLUME) {
            throw new DecoderException("Studio test region exceeds the wire volume limit");
        }
        return region;
    }

    private static void writeStructureOption(FriendlyByteBuf buffer, StudioStructureOption option) {
        buffer.writeResourceLocation(option.id());
        buffer.writeUtf(option.displayName(), 64);
        buffer.writeVarInt(option.size().getX());
        buffer.writeVarInt(option.size().getY());
        buffer.writeVarInt(option.size().getZ());
        buffer.writeBoolean(option.labPlaceable());
    }

    private static StudioStructureOption readStructureOption(FriendlyByteBuf buffer) {
        ResourceLocation id = buffer.readResourceLocation();
        String displayName = buffer.readUtf(64);
        int x = checkedSize(buffer.readVarInt());
        int y = checkedSize(buffer.readVarInt());
        int z = checkedSize(buffer.readVarInt());
        return new StudioStructureOption(id, displayName, new Vec3i(x, y, z), buffer.readBoolean());
    }

    private static void writeStructurePreview(FriendlyByteBuf buffer, StudioStructurePreview preview) {
        buffer.writeResourceLocation(preview.structureId());
        buffer.writeResourceLocation(preview.dimension());
        buffer.writeBlockPos(preview.origin());
        buffer.writeBlockPos(preview.min());
        buffer.writeBlockPos(preview.max());
        buffer.writeEnum(preview.rotation());
        buffer.writeEnum(preview.mirror());
        buffer.writeLong(preview.expiresAtGameTime());
    }

    private static StudioStructurePreview readStructurePreview(FriendlyByteBuf buffer) {
        return new StudioStructurePreview(
                buffer.readResourceLocation(),
                buffer.readResourceLocation(),
                buffer.readBlockPos(),
                buffer.readBlockPos(),
                buffer.readBlockPos(),
                buffer.readEnum(Rotation.class),
                buffer.readEnum(Mirror.class),
                buffer.readLong()
        );
    }

    private static int checkedSize(int value) {
        if (value <= 0 || value > 512) {
            throw new DecoderException("Invalid Studio structure size: " + value);
        }
        return value;
    }

    private static <T> List<T> boundedCopy(List<T> values, int maximum) {
        if (values == null || values.isEmpty()) {
            return List.of();
        }
        return List.copyOf(values.subList(0, Math.min(values.size(), maximum)));
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
