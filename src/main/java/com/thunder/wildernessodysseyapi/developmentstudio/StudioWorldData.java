package com.thunder.wildernessodysseyapi.developmentstudio;

import com.thunder.wildernessodysseyapi.core.ModConstants;
import com.thunder.wildernessodysseyapi.developmentstudio.bookmark.StudioBookmark;
import com.thunder.wildernessodysseyapi.developmentstudio.campus.StudioCampusLayout;
import com.thunder.wildernessodysseyapi.developmentstudio.region.StudioRegionBlockSnapshot;
import com.thunder.wildernessodysseyapi.developmentstudio.region.StudioTestRegion;
import com.thunder.wildernessodysseyapi.developmentstudio.region.StudioTestRegionRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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
    public static final int FORMAT_VERSION = 3;
    public static final String DATA_NAME = ModConstants.MOD_ID + "_development_studio";
    public static final SavedData.Factory<StudioWorldData> FACTORY = new SavedData.Factory<>(
            StudioWorldData::new,
            StudioWorldData::load
    );

    private boolean developmentStudioWorld;
    private boolean campusPlaced;
    private int campusVersion;
    private BlockPos campusOrigin;
    private final List<StudioBookmark> bookmarks = new ArrayList<>();
    private final List<StudioTestRegion> testRegions = new ArrayList<>();
    private final Map<net.minecraft.resources.ResourceLocation, StudioRegionBlockSnapshot> regionSnapshots =
            new LinkedHashMap<>();

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
        data.campusVersion = tag.contains("campus_version", Tag.TAG_INT)
                ? tag.getInt("campus_version")
                : (data.campusPlaced ? StudioCampusLayout.LEGACY_VERSION : 0);
        if (data.campusPlaced && data.campusVersion <= 0) {
            data.campusVersion = StudioCampusLayout.LEGACY_VERSION;
        }
        if (data.campusVersion < 0 || data.campusVersion > StudioCampusLayout.CURRENT_VERSION) {
            throw new IllegalArgumentException("Unsupported Development Campus version: " + data.campusVersion);
        }
        if (tag.contains("campus_origin", Tag.TAG_LONG)) {
            data.campusOrigin = BlockPos.of(tag.getLong("campus_origin"));
        }

        ListTag bookmarksTag = tag.getList("bookmarks", Tag.TAG_COMPOUND);
        for (int index = 0; index < bookmarksTag.size(); index++) {
            StudioBookmark.load(bookmarksTag.getCompound(index)).ifPresent(data.bookmarks::add);
        }

        ListTag regionsTag = tag.getList("test_regions", Tag.TAG_COMPOUND);
        for (int index = 0; index < regionsTag.size(); index++) {
            StudioTestRegion.load(regionsTag.getCompound(index)).ifPresent(data.testRegions::add);
        }
        ListTag snapshotsTag = tag.getList("region_snapshots", Tag.TAG_COMPOUND);
        for (int index = 0; index < snapshotsTag.size(); index++) {
            StudioRegionBlockSnapshot.load(snapshotsTag.getCompound(index)).ifPresent(snapshot ->
                    data.regionSnapshots.put(snapshot.regionId(), snapshot));
        }

        // Version-one worlds already persisted the exact campus origin. Resolve
        // the new bounded regions deterministically without moving the campus.
        if (data.campusOrigin != null && data.testRegions.isEmpty()) {
            data.testRegions.addAll(StudioTestRegionRegistry.resolve(data.campusOrigin));
            data.setDirty();
        }
        return data;
    }

    /** Writes durable identity, campus, bookmarks, test regions, and safe lab baselines. */
    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        tag.putInt("format_version", FORMAT_VERSION);
        tag.putBoolean("development_studio_world", developmentStudioWorld);
        tag.putBoolean("campus_placed", campusPlaced);
        tag.putInt("campus_version", campusVersion);
        if (campusOrigin != null) {
            tag.putLong("campus_origin", campusOrigin.asLong());
        }
        ListTag bookmarksTag = new ListTag();
        bookmarks.forEach(bookmark -> bookmarksTag.add(bookmark.save()));
        tag.put("bookmarks", bookmarksTag);

        ListTag regionsTag = new ListTag();
        testRegions.forEach(region -> regionsTag.add(region.save()));
        tag.put("test_regions", regionsTag);
        ListTag snapshotsTag = new ListTag();
        regionSnapshots.values().forEach(snapshot -> snapshotsTag.add(snapshot.save()));
        tag.put("region_snapshots", snapshotsTag);
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

    public int campusVersion() {
        return campusVersion;
    }

    public boolean needsCampusUpgrade() {
        return isCampusPlaced() && campusVersion < StudioCampusLayout.CURRENT_VERSION;
    }

    /** Records the one-time template placement origin used by registered locations. */
    public void markCampusPlaced(BlockPos origin) {
        markCampusPlaced(origin, StudioCampusLayout.CURRENT_VERSION);
    }

    /** Records a successful versioned placement and rebuilds all relative safety bounds. */
    public void markCampusPlaced(BlockPos origin, int version) {
        if (version <= 0 || version > StudioCampusLayout.CURRENT_VERSION) {
            throw new IllegalArgumentException("Invalid Development Campus version: " + version);
        }
        campusPlaced = true;
        campusVersion = version;
        campusOrigin = origin == null ? null : origin.immutable();
        testRegions.clear();
        regionSnapshots.clear();
        if (campusOrigin != null) {
            testRegions.addAll(StudioTestRegionRegistry.resolve(campusOrigin));
        }
        setDirty();
    }

    /** Returns the immutable, persisted catalog of server-authoritative test areas. */
    public List<StudioTestRegion> testRegions() {
        return List.copyOf(testRegions);
    }

    public Optional<StudioTestRegion> testRegion(net.minecraft.resources.ResourceLocation id) {
        return testRegions.stream().filter(region -> region.id().equals(id)).findFirst();
    }

    public Optional<StudioRegionBlockSnapshot> regionSnapshot(net.minecraft.resources.ResourceLocation regionId) {
        return Optional.ofNullable(regionSnapshots.get(regionId));
    }

    /** Stores the first validated baseline for one registered block-reset region. */
    public void putRegionSnapshotIfAbsent(StudioRegionBlockSnapshot snapshot) {
        if (!regionSnapshots.containsKey(snapshot.regionId())) {
            regionSnapshots.put(snapshot.regionId(), snapshot);
            setDirty();
        }
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
