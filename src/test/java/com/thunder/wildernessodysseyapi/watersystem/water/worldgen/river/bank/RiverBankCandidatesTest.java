package com.thunder.wildernessodysseyapi.watersystem.water.worldgen.river.bank;

import com.thunder.wildernessodysseyapi.watersystem.water.worldgen.river.RiverChannelColumn;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class RiverBankCandidatesTest {
    @Test
    void adjacentDryColumnsStayWithinTheOwningChunkAndNeverIncludeTheChannel() {
        var columns = List.of(column(15, 5), column(16, 5), column(15, 6));
        var west = RiverBankCandidates.select(columns, 0, 0, 256, 42);
        var east = RiverBankCandidates.select(columns, 16, 0, 256, 42);
        assertFalse(west.isEmpty());
        assertFalse(east.isEmpty());
        assertEquals(west.size(), west.stream().distinct().count());
        for (var candidate : west) {
            assertTrue(candidate.x() >= 0 && candidate.x() < 16 && candidate.z() >= 0 && candidate.z() < 16);
            assertTrue(columns.stream().noneMatch(column -> column.x() == candidate.x() && column.z() == candidate.z()));
            assertTrue(columns.stream().anyMatch(column -> Math.abs(column.x() - candidate.x())
                    + Math.abs(column.z() - candidate.z()) == 1));
        }
        for (var candidate : east) {
            assertTrue(candidate.x() >= 0 && candidate.x() < 16 && candidate.z() >= 0 && candidate.z() < 16);
            assertTrue(columns.stream().noneMatch(column -> column.x() == candidate.x() + 16 && column.z() == candidate.z()));
        }
    }

    @Test
    void candidateBudgetAndOrderingAreIndependentOfPlannerListOrder() {
        var one = column(3, 4);
        var two = column(7, 8);
        var expected = RiverBankCandidates.select(List.of(one, two), 0, 0, 3, 42);
        assertEquals(3, expected.size());
        assertEquals(expected, RiverBankCandidates.select(List.of(two, one), 0, 0, 3, 42));
        assertTrue(RiverBankCandidates.select(List.of(one, two), 0, 0, 0, 42).isEmpty());
        assertThrows(UnsupportedOperationException.class, () -> expected.clear());
    }

    private static RiverChannelColumn column(int x, int z) {
        return new RiverChannelColumn(x, z, 100, 99, 100, 1, 0, 0.5f, RiverChannelColumn.Kind.STREAM);
    }
}
