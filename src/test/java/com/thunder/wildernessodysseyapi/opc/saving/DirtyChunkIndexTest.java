package com.thunder.wildernessodysseyapi.opc.saving;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class DirtyChunkIndexTest {
    private static final DirtyChunkIndex.Key OVERWORLD = new DirtyChunkIndex.Key("minecraft:overworld", 7);

    @Test
    void repeatedDirtiesCoalesceAndPostponeHotChunkEligibility() {
        var index = new DirtyChunkIndex(4, 100, 20);
        index.markDirty(OVERWORLD, 0);
        index.markDirty(OVERWORLD, 95);
        assertEquals(1, index.snapshot(110).dirty());
        assertNull(index.candidate(110, 2_000_000));
        assertEquals(OVERWORLD, index.candidate(120, 2_000_000));
    }

    @Test
    void dimensionIsPartOfIdentityAndCapacityOverflowKeepsExistingRecords() {
        var index = new DirtyChunkIndex(2, 100, 20);
        assertTrue(index.markDirty(OVERWORLD, 0));
        assertTrue(index.markDirty(new DirtyChunkIndex.Key("minecraft:the_nether", 7), 0));
        assertFalse(index.markDirty(new DirtyChunkIndex.Key("minecraft:the_end", 7), 0));
        assertEquals(2, index.snapshot(200).dirty());
        assertEquals(1, index.snapshot(200).omitted());
    }

    @Test
    void oldCompletionCannotCleanANewerMutation() {
        var index = new DirtyChunkIndex(4, 100, 20);
        index.markDirty(OVERWORLD, 0);
        long generation = index.begin(OVERWORLD);
        index.markDirty(OVERWORLD, 120);
        assertFalse(index.complete(OVERWORLD, generation, 121, true, 1_000_000));
        assertEquals(1, index.snapshot(121).dirty());
        assertNull(index.candidate(130, 2_000_000));
        assertEquals(OVERWORLD, index.candidate(150, 2_000_000));
    }

    @Test
    void failureRetainsDirtyAndRequestsNativeRemark() {
        var index = new DirtyChunkIndex(4, 100, 20);
        index.markDirty(OVERWORLD, 0);
        long generation = index.begin(OVERWORLD);
        assertTrue(index.complete(OVERWORLD, generation, 120, false, 1_000_000));
        assertEquals(1, index.snapshot(120).dirty());
        assertEquals(0, index.snapshot(120).pending());
    }

    @Test
    void expensivePreviousSaveCannotBeAdmittedWithInsufficientTime() {
        var index = new DirtyChunkIndex(4, 100, 20);
        index.markDirty(OVERWORLD, 0);
        long generation = index.begin(OVERWORLD);
        index.complete(OVERWORLD, generation, 120, true, 4_000_000);
        index.markDirty(OVERWORLD, 121);
        assertNull(index.candidate(240, 2_000_000));
        assertEquals(OVERWORLD, index.candidate(240, 5_000_000));
    }

    @Test
    void nativeDirtyClearCannotEraseAnInFlightOptionalSave() {
        var index = new DirtyChunkIndex(4, 100, 20);
        index.markDirty(OVERWORLD, 0);
        long generation = index.begin(OVERWORLD);
        index.markClean(OVERWORLD);
        assertEquals(1, index.snapshot(120).pending());
        assertTrue(index.complete(OVERWORLD, generation, 120, false, 1_000_000));
        assertEquals(1, index.snapshot(120).dirty());
    }

    @Test
    void unloadedRecordsAndLateCompletionCannotResurrectAChunk() {
        var index = new DirtyChunkIndex(4, 100, 20);
        index.markDirty(OVERWORLD, 0);
        long generation = index.begin(OVERWORLD);
        index.forget(OVERWORLD);
        assertFalse(index.complete(OVERWORLD, generation, 120, false, 1_000_000));
        assertEquals(0, index.snapshot(120).tracked());
    }
}
