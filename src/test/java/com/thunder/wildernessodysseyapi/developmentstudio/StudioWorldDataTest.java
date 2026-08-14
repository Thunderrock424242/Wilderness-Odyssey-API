package com.thunder.wildernessodysseyapi.developmentstudio;

import com.thunder.wildernessodysseyapi.developmentstudio.bookmark.StudioBookmark;
import com.thunder.wildernessodysseyapi.developmentstudio.region.StudioRegionBlockSnapshot;
import com.thunder.wildernessodysseyapi.developmentstudio.region.StudioTestRegion;
import com.thunder.wildernessodysseyapi.developmentstudio.region.StudioTestRegionRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Verifies save/reload persistence for Studio identity, campus, and bookmarks. */
class StudioWorldDataTest {

    @Test
    void worldIdentityCampusAndBookmarksRoundTrip() {
        StudioWorldData data = new StudioWorldData();
        BlockPos campusOrigin = new BlockPos(120, 70, -44);
        UUID bookmarkId = UUID.randomUUID();
        data.markDevelopmentStudioWorld();
        data.markCampusPlaced(campusOrigin);
        StudioTestRegion structureLab = data.testRegion(StudioTestRegionRegistry.STRUCTURE_LAB).orElseThrow();
        CompoundTag stoneState = new CompoundTag();
        stoneState.putString("Name", "minecraft:stone_bricks");
        data.putRegionSnapshotIfAbsent(new StudioRegionBlockSnapshot(
                structureLab.id(),
                structureLab.dimension(),
                structureLab.min(),
                structureLab.max(),
                List.of(new StudioRegionBlockSnapshot.Entry(BlockPos.ZERO, stoneState, null))
        ));
        data.addBookmark(new StudioBookmark(
                bookmarkId,
                "Broken River #4",
                ResourceLocation.withDefaultNamespace("overworld"),
                new BlockPos(320, 63, -901),
                45.0F,
                12.0F,
                ResourceLocation.withDefaultNamespace("river"),
                "Chunk-border continuity regression",
                List.of("water", "river", "chunk-border"),
                123456789L
        ));

        CompoundTag encoded = data.save(new CompoundTag(), null);
        StudioWorldData decoded = StudioWorldData.load(encoded, null);

        assertTrue(decoded.isDevelopmentStudioWorld());
        assertTrue(decoded.isCampusPlaced());
        assertEquals(campusOrigin, decoded.campusOrigin().orElseThrow());
        assertEquals(4, decoded.testRegions().size());
        assertEquals(1, decoded.regionSnapshot(StudioTestRegionRegistry.STRUCTURE_LAB)
                .orElseThrow().entryCount());
        assertEquals(1, decoded.bookmarks().size());
        assertEquals(bookmarkId, decoded.bookmarks().getFirst().id());
        assertEquals("Broken River #4", decoded.bookmarks().getFirst().name());
        assertEquals(List.of("water", "river", "chunk-border"), decoded.bookmarks().getFirst().tags());
    }

    @Test
    void versionOneWorldMigratesRegionsFromPersistedCampusOrigin() {
        CompoundTag versionOne = new CompoundTag();
        versionOne.putInt("format_version", 1);
        versionOne.putBoolean("development_studio_world", true);
        versionOne.putBoolean("campus_placed", true);
        versionOne.putLong("campus_origin", new BlockPos(20, 70, 30).asLong());

        StudioWorldData migrated = StudioWorldData.load(versionOne, null);

        assertEquals(4, migrated.testRegions().size());
        assertEquals(new BlockPos(21, 70, 38), migrated.testRegion(
                StudioTestRegionRegistry.STRUCTURE_LAB).orElseThrow().min());
    }
}
