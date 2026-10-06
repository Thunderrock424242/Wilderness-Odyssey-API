package com.thunder.wildernessodysseyapi.loading;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LoadingProfileSessionTest {
    @Test
    void countsStageAndPercentageChangesInsteadOfRepeatedClientHeartbeats() {
        LoadingProfileSession session = session();
        session.observe("Generating spawn", 10, seconds(1));
        session.observe("Generating spawn", 10, seconds(5));

        var snapshot = session.snapshot(seconds(8));
        assertEquals(seconds(7), snapshot.noProgressNanos());
        assertEquals(seconds(3), snapshot.clientSilenceNanos());

        session.observe("Generating spawn", 11, seconds(9));
        assertEquals(seconds(1), session.snapshot(seconds(10)).noProgressNanos());
        session.observe("Receiving terrain", -1, seconds(11));
        assertEquals(seconds(1), session.snapshot(seconds(12)).noProgressNanos());
    }

    @Test
    void accountsForTimeAcrossLoadingStages() {
        LoadingProfileSession session = session();
        session.observe("World data", -1, 0);
        session.observe("Generating spawn", 0, seconds(3));
        var snapshot = session.snapshot(seconds(8));

        assertEquals(seconds(8), snapshot.elapsedNanos());
        assertEquals(seconds(3), snapshot.phases().get("World data"));
        assertEquals(seconds(5), snapshot.phases().get("Generating spawn"));
    }

    @Test
    void attributesModFramesBelowJavaAndMinecraftWithoutBlamingWaitingForActiveWork() {
        LoadingProfileSession session = session();
        List<StackTraceElement> stack = List.of(
                frame("java.util.concurrent.CompletableFuture", "join"),
                frame("net.minecraft.server.level.ServerChunkCache", "getChunk"),
                frame("example.terrain.Generator", "generate"));
        session.sample(List.of(new LoadingProfileSession.ThreadSample(1, "Server thread",
                Thread.State.WAITING, stack, 100)));
        session.sample(List.of(new LoadingProfileSession.ThreadSample(1, "Server thread",
                Thread.State.RUNNABLE, stack, 400)));

        var snapshot = session.snapshot(seconds(2));
        assertEquals(2, snapshot.samples());
        assertEquals("terrainmod", snapshot.sites().getFirst().owner());
        assertEquals("example.terrain.Generator.generate", snapshot.sites().getFirst().location());
        assertEquals(1, snapshot.sites().getFirst().runnableSamples());
        assertEquals(1, snapshot.sites().getFirst().waitingSamples());
        assertEquals(300, snapshot.threads().getFirst().cpuNanos());
    }

    @Test
    void neverTurnsUnavailableOrResetCpuCountersIntoCpuTime() {
        LoadingProfileSession session = session();
        for (long cpu : new long[]{-1, 500, 100, 200}) {
            session.sample(List.of(sample(1, "Worker-Main-1", "example.terrain.Generator", cpu)));
        }
        assertEquals(100, session.snapshot(0).threads().getFirst().cpuNanos());
    }

    @Test
    void excludesTheProfilerItselfAndRetainsUnknownOwnershipHonestly() {
        LoadingProfileSession session = session();
        session.sample(List.of(
                sample(1, "WO-Loading-Profiler", "example.terrain.Generator", 100),
                sample(2, "Worker-Main-1", "unresolved.SomeFeature", -1)));
        var snapshot = session.snapshot(0);

        assertEquals(1, snapshot.threads().size());
        assertEquals("unresolved", snapshot.sites().getFirst().owner());
    }

    @Test
    void boundsThreadAndSiteHistoryDuringAnIndefiniteLoad() {
        LoadingProfileSession session = session();
        List<LoadingProfileSession.ThreadSample> threads = new ArrayList<>();
        for (int index = 0; index < 300; index++) {
            threads.add(sample(index, "Worker-" + index, "example.terrain.Generator", index));
        }
        session.sample(threads);
        for (int index = 0; index < 400; index++) {
            session.sample(List.of(sample(0, "Worker-0", "unresolved.Feature" + index, index)));
        }

        var snapshot = session.snapshot(0);
        assertEquals(128, snapshot.threads().size());
        assertEquals(256, snapshot.sites().size());
        assertTrue(snapshot.droppedThreads() > 0);
        assertTrue(snapshot.droppedSites() > 0);
    }

    @Test
    void reportsSamplingLimitsAndWaitingSeparatelyFromMeasuredCpu() {
        LoadingProfileSession session = session();
        session.observe("Generating spawn", 25, seconds(1));
        session.sample(List.of(sample(1, "Worker-Main-1", "example.terrain.Generator", -1)));

        String report = session.report(seconds(10), "Loading screen closed", List.of("terrainmod@1.0"));
        assertTrue(report.contains("terrainmod"));
        assertTrue(report.contains("25%"));
        assertTrue(report.contains("CPU unavailable"));
        assertTrue(report.contains("not percentages of total world-loading time"));
        assertTrue(report.contains("terrainmod@1.0"));
        assertFalse(report.contains("Likely Culprit"));
    }

    @Test
    void clockRollbackDoesNotProduceNegativeDurations() {
        LoadingProfileSession session = new LoadingProfileSession(seconds(10), ignored -> "unresolved");
        session.observe("Generating spawn", 0, seconds(10));
        var snapshot = session.snapshot(seconds(5));
        assertEquals(0, snapshot.elapsedNanos());
        assertEquals(0, snapshot.noProgressNanos());
        assertEquals(0, snapshot.clientSilenceNanos());
    }

    private static LoadingProfileSession session() {
        return new LoadingProfileSession(0, frame -> frame.getClassName().startsWith("example.terrain.")
                ? "terrainmod" : frame.getClassName().startsWith("net.minecraft.") ? "minecraft" : "unresolved");
    }

    private static LoadingProfileSession.ThreadSample sample(long id, String name, String className, long cpu) {
        return new LoadingProfileSession.ThreadSample(id, name, Thread.State.RUNNABLE,
                List.of(frame(className, "generate")), cpu);
    }

    private static StackTraceElement frame(String className, String method) {
        return new StackTraceElement(className, method, "Source.java", 42);
    }

    private static long seconds(long seconds) {
        return seconds * 1_000_000_000L;
    }
}
