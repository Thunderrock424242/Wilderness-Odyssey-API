package com.thunder.wildernessodysseyapi.temporalrift;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import org.junit.jupiter.api.Test;
import java.util.HashSet;
import static org.junit.jupiter.api.Assertions.*;

class EchoBuildEchoSavedDataTest {
    private static EchoBuildEcho echo(int x, long day) {
        return new EchoBuildEcho(new BlockPos(x, 64, 0), new BlockPos(x + 2, 62, 0), day,
                "wood", "Explorer", TemporalEcho.Type.PLACE);
    }

    @Test
    void capacityDeclinesNewSamplesWithoutEvictingAcceptedRecords() {
        var data = new EchoBuildEchoSavedData();
        var first = echo(1, 3);
        var second = echo(2, 4);
        data.addEcho(first, 2);
        data.addEcho(second, 2);
        data.addEcho(echo(3, 5), 2);
        assertEquals(java.util.List.of(first, second), data.pendingEchoes());
    }

    @Test
    void repeatedEditsCoalesceAndAnOldBatchCannotRemoveTheNewRecord() {
        var data = new EchoBuildEchoSavedData();
        var first = echo(1, 3);
        var latest = echo(1, 7);
        data.addEcho(first, 1);
        data.addEcho(latest, 1);
        data.removeEcho(first);
        assertEquals(java.util.List.of(latest), data.pendingEchoes());
    }

    @Test
    void boundedBatchesEventuallyVisitAllUnloadedDestinations() {
        var data = new EchoBuildEchoSavedData();
        for (int i = 0; i < 130; i++) data.addEcho(echo(i, 5), 130);
        var visited = new HashSet<BlockPos>();
        for (int i = 0; i < 3; i++) {
            var batch = data.nextBatch(64);
            assertEquals(64, batch.size());
            batch.forEach(value -> visited.add(value.sourcePos()));
        }
        assertEquals(130, visited.size());
        assertEquals(130, data.size());
    }

    @Test
    void failedWorldWriteDoesNotRemovePendingEcho() {
        var data = new EchoBuildEchoSavedData();
        var pending = echo(10, 4);
        data.addEcho(pending, 2);
        assertFalse(EchoBuildEchoManager.shouldDequeue(EchoBuildEchoManager.ApplyResult.RETRY));
        assertTrue(EchoBuildEchoManager.shouldDequeue(EchoBuildEchoManager.ApplyResult.APPLIED));
        assertTrue(EchoBuildEchoManager.shouldDequeue(EchoBuildEchoManager.ApplyResult.SKIPPED));
        assertEquals(java.util.List.of(pending), data.pendingEchoes());
    }

    @Test
    void originalSavedRecordsAndMissingLegacyTypeRemainReadable() {
        var original = echo(-12, 123);
        CompoundTag record = original.save();
        record.remove("type");
        ListTag list = new ListTag();
        list.add(record);
        CompoundTag root = new CompoundTag();
        root.put("echoes", list);
        var restored = EchoBuildEchoSavedData.load(root, null);
        var value = restored.pendingEchoes().getFirst();
        assertEquals(original.sourcePos(), value.sourcePos());
        assertEquals(original.targetPos(), value.targetPos());
        assertEquals(original.revealDay(), value.revealDay());
        assertEquals("wood", value.materialKey());
        assertEquals(TemporalEcho.Type.PLACE, value.type());
        var roundTrip = EchoBuildEchoSavedData.load(restored.save(new CompoundTag(), null), null);
        assertEquals(value.targetPos(), roundTrip.pendingEchoes().getFirst().targetPos());
    }
}
