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
    private static volatile RenderPath renderPath = RenderPath.DISABLED;
    private static volatile boolean sceneCaptureAvailable;
    private static volatile boolean externalRendererBridgeObserved;

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

    /** Records which renderer currently owns Wilderness water pixels. */
    public static void setRenderPath(RenderPath path) {
        renderPath = path == null ? RenderPath.DISABLED : path;
    }

    /** Records whether the optical pass has a valid color/depth scene copy. */
    public static void setSceneCaptureAvailable(boolean available) {
        sceneCaptureAvailable = available;
    }

    /** Records that the optional Sodium fluid bridge is active. */
    public static void recordExternalRendererBridgeUse() {
        externalRendererBridgeObserved = true;
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
                MESH_REBUILDS.get(),
                renderPath,
                sceneCaptureAvailable,
                externalRendererBridgeObserved
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
                "WO Water SSR optical GPU: " + formatMillis(frame.ssrNanos()),
                "WO Water path: " + snapshot.renderPath().label + " | scene capture "
                        + (snapshot.sceneCaptureAvailable() ? "ready" : "fallback") + " | "
                        + WaterRenderingConfig.profileName() + " | reflections "
                        + (snapshot.sceneCaptureAvailable()
                        ? WaterRenderingConfig.reflectionProfileName()
                        : "environment fallback"),
                "WO Water quality: " + WaterRenderingConfig.qualitySelectionSummary(),
                "WO Water shader-pack alias: "
                        + ExternalShaderWaterMaterialBridge.status().label()
                        + " | renderer bridge "
                        + (snapshot.externalRendererBridgeObserved() ? "active" : "not observed")
        );
    }

    // Keep the overlay compact while retaining enough precision for frame-cost regressions.
    private static String formatMillis(long nanos) {
        if (nanos < 0L) {
            return "pending/unavailable";
        }
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
        private static final FrameStats EMPTY = new FrameStats(0, 0, 0, 0, 0L, -1L);
    }

    /** Pixel owner selected by the coordinator for the current client frame. */
    public enum RenderPath {
        DISABLED("disabled"),
        CORE_SHADER("built-in optical shader"),
        VANILLA_FALLBACK("vanilla-safe fallback"),
        EXTERNAL_SHADER_PACK("external shader pack");

        private final String label;

        RenderPath(String label) {
            this.label = label;
        }
    }

    /** Complete client water diagnostics view. */
    public record Snapshot(FrameStats frame, long snapshotBytes, long generatedMetadataBytes, long sceneCopyNanos,
                           long sceneCopyCount, long meshRebuildCount, RenderPath renderPath,
                           boolean sceneCaptureAvailable, boolean externalRendererBridgeObserved) {
    }
}
