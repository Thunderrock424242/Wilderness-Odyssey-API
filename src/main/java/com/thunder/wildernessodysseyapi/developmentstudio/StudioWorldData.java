package com.thunder.wildernessodysseyapi.developmentstudio;

import com.thunder.wildernessodysseyapi.core.ModConstants;
import com.thunder.wildernessodysseyapi.developmentstudio.bookmark.StudioBookmark;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Persistent, world-level Development Studio metadata stored in the Overworld.
 *
 * <p>This is the runtime source of truth for Studio identity. The world preset's
 * vanilla-derived noise-settings holder is consulted only while initializing
 * this data on first load.</p>
 */
public final class StudioWorldData extends SavedData {
    public static final int FORMAT_VERSION = 1;
    public static final String DATA_NAME = ModConstants.MOD_ID + "_development_studio";
    public static final SavedData.Factory<StudioWorldData> FACTORY = new SavedData.Factory<>(
            StudioWorldData::new,
            StudioWorldData::load
    );

    private boolean developmentStudioWorld;
    private boolean campusPlaced;
    private BlockPos campusOrigin;
    private final List<StudioBookmark> bookmarks = new ArrayList<>();

    /** Creates empty metadata for a normal or explicitly enabled test world. */
    public StudioWorldData() {
    }

    /** Returns existing Studio metadata without creating a file in normal worlds. */
    public static Optional<StudioWorldData> find(MinecraftServer server) {
        if (server == null || server.overworld() == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(server.overworld().getDataStorage().get(FACTORY, DATA_NAME));
    }

    /** Returns the world metadata, creating it for an authorized Studio operation. */
    public static StudioWorldData getOrCreate(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(FACTORY, DATA_NAME);
    }

    /** Decodes Studio metadata from the Overworld data directory. */
    public static StudioWorldData load(CompoundTag tag, HolderLookup.Provider registries) {
        StudioWorldData data = new StudioWorldData();
        int version = tag.getInt("format_version");
        if (version > FORMAT_VERSION) {
            throw new IllegalArgumentException("Unsupported Development Studio data version: " + version);
        }

        data.developmentStudioWorld = tag.getBoolean("development_studio_world");
        data.campusPlaced = tag.getBoolean("campus_placed");
        if (tag.contains("campus_origin", Tag.TAG_LONG)) {
            data.campusOrigin = BlockPos.of(tag.getLong("campus_origin"));
        }

        ListTag bookmarksTag = tag.getList("bookmarks", Tag.TAG_COMPOUND);
        for (int index = 0; index < bookmarksTag.size(); index++) {
            StudioBookmark.load(bookmarksTag.getCompound(index)).ifPresent(data.bookmarks::add);
        }
        return data;
    }

    /** Writes the durable Studio identity, campus state, and bookmark catalog. */
    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        tag.putInt("format_version", FORMAT_VERSION);
        tag.putBoolean("development_studio_world", developmentStudioWorld);
        tag.putBoolean("campus_placed", campusPlaced);
        if (campusOrigin != null) {
            tag.putLong("campus_origin", campusOrigin.asLong());
        }
        ListTag bookmarksTag = new ListTag();
        bookmarks.forEach(bookmark -> bookmarksTag.add(bookmark.save()));
        tag.put("bookmarks", bookmarksTag);
        return tag;
    }

    /** Marks the world as having originated from the Development Studio preset. */
    public void markDevelopmentStudioWorld() {
        if (!developmentStudioWorld) {
            developmentStudioWorld = true;
            setDirty();
        }
    }

    public boolean isDevelopmentStudioWorld() {
        return developmentStudioWorld;
    }

    public boolean isCampusPlaced() {
        return campusPlaced && campusOrigin != null;
    }

    public Optional<BlockPos> campusOrigin() {
        return Optional.ofNullable(campusOrigin);
    }

    /** Records the one-time template placement origin used by registered locations. */
    public void markCampusPlaced(BlockPos origin) {
        campusPlaced = true;
        campusOrigin = origin == null ? null : origin.immutable();
        setDirty();
    }

    public List<StudioBookmark> bookmarks() {
        return List.copyOf(bookmarks);
    }

    public Optional<StudioBookmark> bookmark(UUID id) {
        return bookmarks.stream().filter(bookmark -> bookmark.id().equals(id)).findFirst();
    }

    public void addBookmark(StudioBookmark bookmark) {
        bookmarks.add(bookmark);
        setDirty();
    }

    public boolean updateBookmark(StudioBookmark updated) {
        for (int index = 0; index < bookmarks.size(); index++) {
            if (bookmarks.get(index).id().equals(updated.id())) {
                bookmarks.set(index, updated);
                setDirty();
                return true;
            }
        }
        return false;
    }

    public boolean removeBookmark(UUID id) {
        boolean removed = bookmarks.removeIf(bookmark -> bookmark.id().equals(id));
        if (removed) {
            setDirty();
        }
        return removed;
    }
}
