package com.thunder.wildernessodysseyapi.watersystem.water.render;

import java.util.List;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Collects bounded water-render counters without forcing synchronous GPU reads.
 *
 * <p>The coordinator can populate mesh and snapshot counters incrementally.
 * Scene-copy timing measures CPU submission cost; a later asynchronous GPU
 * timer can report actual completion without stalling the render thread.</p>
 */
public final class WaterRenderDiagnostics {

    private static final AtomicLong SCENE_COPY_NANOS = new AtomicLong();
    private static final AtomicLong SCENE_COPY_COUNT = new AtomicLong();
    private static final AtomicLong MESH_REBUILDS = new AtomicLong();
    private static final AtomicLong SNAPSHOT_BYTES = new AtomicLong();
    private static final AtomicLong GENERATED_METADATA_BYTES = new AtomicLong();
    private static volatile FrameStats frameStats = FrameStats.EMPTY;

    private WaterRenderDiagnostics() {
    }

    /** Records one framebuffer-copy submission. */
    public static void recordSceneCopy(long elapsedNanos) {
        SCENE_COPY_NANOS.set(Math.max(0L, elapsedNanos));
        SCENE_COPY_COUNT.incrementAndGet();
    }

    /** Records one completed cached-mesh rebuild. */
    public static void recordMeshRebuild() {
        MESH_REBUILDS.incrementAndGet();
    }

    /** Updates the compact client snapshot memory estimate. */
    public static void setSnapshotBytes(long bytes) {
        SNAPSHOT_BYTES.set(Math.max(0L, bytes));
    }

    /** Updates the persistent generated-water metadata size estimate. */
    public static void setGeneratedMetadataBytes(long bytes) {
        GENERATED_METADATA_BYTES.set(Math.max(0L, bytes));
    }

    /** Publishes the immutable counters for the most recently completed frame. */
    public static void publishFrame(int visibleGroups, int culledGroups, int vertices, int triangles,
                                    long waterRenderNanos, long ssrNanos) {
        frameStats = new FrameStats(
                Math.max(0, visibleGroups),
                Math.max(0, culledGroups),
                Math.max(0, vertices),
                Math.max(0, triangles),
                Math.max(0L, waterRenderNanos),
                Math.max(0L, ssrNanos)
        );
    }

    /** Returns a lock-free immutable diagnostics snapshot. */
    public static Snapshot snapshot() {
        return new Snapshot(
                frameStats,
                SNAPSHOT_BYTES.get(),
                GENERATED_METADATA_BYTES.get(),
                SCENE_COPY_NANOS.get(),
                SCENE_COPY_COUNT.get(),
                MESH_REBUILDS.get()
        );
    }

    /**
     * Formats the latest counters for Minecraft's F3 system-information panel.
     *
     * <p>The method reads one immutable snapshot so values from different
     * frames are not mixed while the render coordinator publishes updates.</p>
     */
    public static List<String> debugLines() {
        Snapshot snapshot = snapshot();
        FrameStats frame = snapshot.frame();
        return List.of(
                "WO Water mesh: " + frame.visibleGroups() + " visible / " + frame.culledGroups()
                        + " culled | " + frame.vertices() + " vtx / " + frame.triangles()
                        + " tri | " + snapshot.meshRebuildCount() + " rebuilds",
                "WO Water memory: " + formatBytes(snapshot.snapshotBytes()) + " snapshots | "
                        + formatBytes(snapshot.generatedMetadataBytes()) + " generated",
                "WO Water CPU: " + formatMillis(frame.waterRenderNanos()) + " render | "
                        + formatMillis(snapshot.sceneCopyNanos()) + " scene copy x"
                        + snapshot.sceneCopyCount(),
                "WO Water SSR optical GPU: " + formatMillis(frame.ssrNanos())
        );
    }

    // Keep the overlay compact while retaining enough precision for frame-cost regressions.
    private static String formatMillis(long nanos) {
        return String.format(Locale.ROOT, "%.3f ms", nanos / 1_000_000.0);
    }

    private static String formatBytes(long bytes) {
        if (bytes < 1_024L) {
            return bytes + " B";
        }
        if (bytes < 1_048_576L) {
            return String.format(Locale.ROOT, "%.1f KiB", bytes / 1_024.0);
        }
        return String.format(Locale.ROOT, "%.2f MiB", bytes / 1_048_576.0);
    }

    /** Per-frame geometry and timing counters. */
    public record FrameStats(int visibleGroups, int culledGroups, int vertices, int triangles,
                             long waterRenderNanos, long ssrNanos) {
        private static final FrameStats EMPTY = new FrameStats(0, 0, 0, 0, 0L, 0L);
    }

    /** Complete client water diagnostics view. */
    public record Snapshot(FrameStats frame, long snapshotBytes, long generatedMetadataBytes, long sceneCopyNanos,
                           long sceneCopyCount, long meshRebuildCount) {
    }
}
