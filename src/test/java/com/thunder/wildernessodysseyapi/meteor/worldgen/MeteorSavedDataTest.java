package com.thunder.wildernessodysseyapi.meteor.worldgen;

import com.thunder.wildernessodysseyapi.meteor.api.MeteorSiteSource;
import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Verifies meteor-site indexing and source metadata without a loaded server world. */
class MeteorSavedDataTest {

    @Test
    void boundedQueriesCrossIndexRegionBordersAndChooseNearestSite() {
        MeteorSavedData data = new MeteorSavedData();
        data.addMeteor(new BlockPos(250, 70, 0), 12, 100L, 0.75, MeteorSiteSource.WORLDGEN);
        MeteorSavedData.MeteorRecord nearest = data.addMeteor(
                new BlockPos(270, 70, 0), 20, 200L, 1.0, MeteorSiteSource.RIFTFALL);
        data.addMeteor(new BlockPos(900, 70, 0), 16, 300L, 1.0, MeteorSiteSource.NATURAL);

        assertEquals(2, data.findWithin(new BlockPos(260, 64, 0), 32).size());
        assertEquals(nearest, data.findNearest(new BlockPos(269, 64, 0), 32).orElseThrow());
        assertEquals(MeteorSiteSource.RIFTFALL, nearest.source());
        assertEquals(200L, nearest.createdAt());
    }

    @Test
    void duplicateSuccessfulPublicationKeepsOneAuthoritativeRecord() {
        MeteorSavedData data = new MeteorSavedData();
        BlockPos center = new BlockPos(-16, 72, -16);
        MeteorSavedData.MeteorRecord first = data.addMeteor(
                center, 18, 50L, 1.0, MeteorSiteSource.COMMAND);
        MeteorSavedData.MeteorRecord duplicate = data.addMeteor(
                center, 30, 80L, 0.5, MeteorSiteSource.NATURAL);

        assertSame(first, duplicate);
        assertEquals(1, data.getMeteors().size());
        assertTrue(data.findNearest(center, 0).isPresent());
    }
}
