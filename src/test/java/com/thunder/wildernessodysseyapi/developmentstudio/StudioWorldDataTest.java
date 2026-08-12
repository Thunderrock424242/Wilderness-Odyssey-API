package com.thunder.wildernessodysseyapi.developmentstudio;

import com.thunder.wildernessodysseyapi.developmentstudio.bookmark.StudioBookmark;
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
        assertEquals(1, decoded.bookmarks().size());
        assertEquals(bookmarkId, decoded.bookmarks().getFirst().id());
        assertEquals("Broken River #4", decoded.bookmarks().getFirst().name());
        assertEquals(List.of("water", "river", "chunk-border"), decoded.bookmarks().getFirst().tags());
    }
}
