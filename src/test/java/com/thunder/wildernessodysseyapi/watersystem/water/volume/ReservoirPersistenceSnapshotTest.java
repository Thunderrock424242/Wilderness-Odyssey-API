package com.thunder.wildernessodysseyapi.watersystem.water.volume;

import org.junit.jupiter.api.Test;

import java.util.Properties;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class ReservoirPersistenceSnapshotTest {

    @Test
    void propertiesRoundTripPreservesEveryCellField() {
        var snapshot = snapshot();
        var restored = ReservoirPersistenceSnapshot.decode(snapshot.encode());
        assertEquals("filled", restored.stage());
        assertEquals(1234, restored.gameTime());
        assertDoesNotThrow(() -> restored.verifyCells(cells()));
    }

    @Test
    void lostUnitsCannotMatchAnExpectedCheckpoint() {
        int[] changed = cells();
        changed[0]--;
        assertThrows(IllegalStateException.class, () -> snapshot().verifyCells(changed));
    }

    @Test
    void flagsTemperatureAndVelocityAreNotReducedToAVolumeCheck() {
        for (int field : new int[]{1, 4, 5}) {
            int[] changed = cells();
            changed[field]++;
            assertThrows(IllegalStateException.class, () -> snapshot().verifyCells(changed));
        }
    }

    @Test
    void incompleteOrFutureSnapshotsAreRejected() {
        Properties incomplete = snapshot().encode();
        incomplete.remove("cells");
        assertThrows(IllegalArgumentException.class, () -> ReservoirPersistenceSnapshot.decode(incomplete));
        Properties future = snapshot().encode();
        future.setProperty("schema", "2");
        assertThrows(IllegalArgumentException.class, () -> ReservoirPersistenceSnapshot.decode(future));
    }

    @Test
    void malformedInventoryCannotBecomeAnExpectedOwner() {
        int[] changed = cells();
        changed[0] = 4097;
        assertThrows(IllegalArgumentException.class, () -> create(changed));
        assertThrows(IllegalArgumentException.class, () -> create(new int[6]));
    }

    @Test
    void resumedStagesRequireTheSameRunAndADifferentProcessExecution() {
        assertDoesNotThrow(() -> snapshot().verifyPrevious("disk-test", "filled", "next-execution", 42));
        assertThrows(IllegalStateException.class, () -> snapshot().verifyPrevious("other-run", "filled", "next-execution", 42));
        assertThrows(IllegalStateException.class, () -> snapshot().verifyPrevious("disk-test", "draining", "next-execution", 42));
        assertThrows(IllegalStateException.class, () -> snapshot().verifyPrevious("disk-test", "filled", "first-execution", 42));
        assertThrows(IllegalStateException.class, () -> snapshot().verifyPrevious("disk-test", "filled", "next-execution", 41));
    }

    @Test
    void ordinaryWorldDirectoriesCannotActivateTheProbe() {
        assertDoesNotThrow(() -> ReservoirPersistenceSnapshot.verifyDirectory("disk-test", "execution",
                Path.of("run-water-persistence-disk-test")));
        assertThrows(IllegalStateException.class, () -> ReservoirPersistenceSnapshot.verifyDirectory("disk-test", "execution", Path.of("run")));
        assertThrows(IllegalStateException.class, () -> ReservoirPersistenceSnapshot.verifyDirectory("../other", "execution", Path.of("run")));
    }

    private static ReservoirPersistenceSnapshot snapshot() { return create(cells()); }

    private static ReservoirPersistenceSnapshot create(int[] cells) {
        return new ReservoirPersistenceSnapshot("disk-test", "filled", "first-execution", 41, 42, 1234, cells);
    }

    private static int[] cells() {
        int[] cells = new int[108];
        for (int cell = 0; cell < 18; cell++) cells[cell * 6 + 5] = 293_150;
        for (int cell = 0; cell < 4; cell++) cells[cell * 6] = 4096;
        cells[1] = Float.floatToIntBits(1.25f);
        return cells;
    }
}
