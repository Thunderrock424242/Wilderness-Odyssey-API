package com.thunder.wildernessodysseyapi.watersystem.water.volume;

import java.util.Arrays;
import java.util.Properties;
import java.nio.file.Path;
import java.util.stream.Collectors;

/** Bounded assertion data for the dedicated-server probe; never a source of gameplay water. */
record ReservoirPersistenceSnapshot(String runId, String stage, String execution,
                                    long processId, long seed, long gameTime, int[] cells) {

    static final int CELL_COUNT = 18;
    static final int FIELDS = 6;
    static final int FUNDED_UNITS = 16_384;

    ReservoirPersistenceSnapshot {
        if (runId == null || !runId.matches("[a-z0-9-]{1,64}")
                || !Arrays.asList("filled", "draining", "drained").contains(stage)
                || execution == null || !execution.matches("[A-Za-z0-9-]{1,100}")
                || processId <= 0 || gameTime < 0 || cells == null || cells.length != CELL_COUNT * FIELDS) {
            throw new IllegalArgumentException("Invalid reservoir checkpoint header");
        }
        cells = cells.clone();
        int total = 0;
        for (int cell = 0; cell < CELL_COUNT; cell++) {
            int start = cell * FIELDS;
            if (cells[start] < 0 || cells[start] > 4096 || cells[start + 5] <= 0) {
                throw new IllegalArgumentException("Invalid checkpoint cell " + cell);
            }
            for (int field = 1; field <= 3; field++) {
                if (!Float.isFinite(Float.intBitsToFloat(cells[start + field]))) {
                    throw new IllegalArgumentException("Non-finite checkpoint motion");
                }
            }
            total += cells[start];
        }
        if (total != FUNDED_UNITS) throw new IllegalArgumentException("Checkpoint has an invalid conservation total: " + total);
    }

    @Override public int[] cells() { return cells.clone(); }

    Properties encode() {
        Properties data = new Properties();
        data.setProperty("schema", "1");
        data.setProperty("run", runId);
        data.setProperty("stage", stage);
        data.setProperty("execution", execution);
        data.setProperty("pid", Long.toString(processId));
        data.setProperty("seed", Long.toString(seed));
        data.setProperty("gameTime", Long.toString(gameTime));
        data.setProperty("cells", Arrays.stream(cells).mapToObj(Integer::toString).collect(Collectors.joining(",")));
        return data;
    }

    static ReservoirPersistenceSnapshot decode(Properties data) {
        if (!"1".equals(required(data, "schema"))) throw new IllegalArgumentException("Unsupported checkpoint schema");
        String[] fields = required(data, "cells").split(",", -1);
        if (fields.length != CELL_COUNT * FIELDS) throw new IllegalArgumentException("Truncated/oversized checkpoint cells");
        int[] cells = Arrays.stream(fields).mapToInt(Integer::parseInt).toArray();
        return new ReservoirPersistenceSnapshot(required(data, "run"), required(data, "stage"), required(data, "execution"),
                Long.parseLong(required(data, "pid")), Long.parseLong(required(data, "seed")),
                Long.parseLong(required(data, "gameTime")), cells);
    }

    void verifyPrevious(String expectedRun, String expectedStage, String currentExecution, long currentSeed) {
        if (!runId.equals(expectedRun) || !stage.equals(expectedStage) || execution.equals(currentExecution) || seed != currentSeed) {
            throw new IllegalStateException("Checkpoint run/stage/process execution/world seed does not match");
        }
    }

    void verifyCells(int[] observed) {
        if (observed.length != cells.length) throw new IllegalStateException("Persisted fixture cell count changed");
        for (int index = 0; index < cells.length; index++) {
            if (cells[index] != observed[index]) throw new IllegalStateException("Persisted water differs at cell "
                    + index / FIELDS + ", field " + index % FIELDS + ": expected=" + cells[index] + ", actual=" + observed[index]);
        }
    }

    static void verifyDirectory(String run, String execution, Path directory) {
        if (run == null || !run.matches("[a-z0-9-]{1,64}") || execution == null || execution.isBlank()
                || directory == null || directory.getFileName() == null
                || !directory.getFileName().toString().equals("run-water-persistence-" + run)) {
            throw new IllegalStateException("Persistence probe requires its isolated dedicated-server profile directory");
        }
    }

    private static String required(Properties data, String key) {
        String value = data.getProperty(key);
        if (value == null || value.isBlank()) throw new IllegalArgumentException("Missing checkpoint field " + key);
        return value;
    }
}
