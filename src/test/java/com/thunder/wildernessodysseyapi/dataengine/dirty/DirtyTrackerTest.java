package com.thunder.wildernessodysseyapi.dataengine.dirty;

import com.thunder.wildernessodysseyapi.dataengine.queue.UpdatePriority;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DirtyTrackerTest {
    private static final ResourceLocation SYSTEM = ResourceLocation.fromNamespaceAndPath("test", "dirty");

    @Test
    void marksLooksUpAndClearsActiveKey() {
        DirtyTracker tracker = new DirtyTracker(8);

        assertEquals(DirtyTracker.MarkResult.ACCEPTED,
                tracker.markDirty(SYSTEM, 42L, UpdatePriority.NORMAL, "initial", 10L));
        assertTrue(tracker.isDirty(SYSTEM, 42L));
        assertTrue(tracker.clearDirty(SYSTEM, 42L));
        assertFalse(tracker.isDirty(SYSTEM, 42L));
    }

    @Test
    void duplicateMarkUpdatesOneEntryAndPromotesPriority() {
        DirtyTracker tracker = new DirtyTracker(8);
        tracker.markDirty(SYSTEM, 7L, UpdatePriority.LOW, "first", 1L);

        assertEquals(DirtyTracker.MarkResult.COALESCED,
                tracker.markDirty(SYSTEM, 7L, UpdatePriority.HIGH, "latest", 4L));
        assertEquals(1, tracker.size());

        DirtyEntry entry = tracker.pollNonCritical();
        assertEquals(UpdatePriority.HIGH, entry.priority());
        assertEquals("latest", entry.reason());
        assertEquals(4L, entry.markedTick());
        assertEquals(2, entry.markCount());
    }
}
