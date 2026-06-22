package com.thunder.wildernessodysseyapi.gpuprofiler.client;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.thunder.wildernessodysseyapi.core.ModConstants;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.fml.ModList;
import net.neoforged.neoforgespi.language.IModInfo;
import org.lwjgl.opengl.GL11;

import java.io.IOException;
import java.net.URI;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.CodeSource;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * Short-lived, client-only allocation profiler for Minecraft's OpenGL paths.
 * It tracks logical allocations and correlates them with vendor memory counters
 * when the active driver exposes one of the supported extensions.
 */
public final class GpuProfiler {

    private static final Object LOCK = new Object();
    private static final long SAMPLE_INTERVAL_NANOS = Duration.ofMillis(250).toNanos();
    private static final int MAX_SAMPLES = 20_000;
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final StackWalker STACK_WALKER = StackWalker.getInstance(
            EnumSet.of(StackWalker.Option.RETAIN_CLASS_REFERENCE), 48
    );
    private static final DateTimeFormatter FILE_TIME = DateTimeFormatter
            .ofPattern("yyyyMMdd-HHmmss-SSS")
            .withZone(ZoneOffset.UTC);

    private static final Map<ResourceKey, TrackedResource> RESOURCES = new HashMap<>();
    private static final Map<Class<?>, String> MOD_CACHE = new HashMap<>();
    private static final List<GpuMemoryProbe.Sample> SAMPLES = new ArrayList<>();
    private static final List<SnapshotData> SNAPSHOTS = new ArrayList<>();

    private static volatile boolean active;
    private static volatile List<String> cachedDebugLines = List.of();
    private static Instant startedAt;
    private static Instant stoppedAt;
    private static long startedNanos;
    private static long nextSampleNanos;
    private static long allocationEvents;
    private static long deletionEvents;
    private static long allocatedBytes;
    private static long releasedBytes;
    private static int hookErrors;
    private static GpuMemoryProbe.GpuInfo gpuInfo = new GpuMemoryProbe.GpuInfo("unavailable", "unavailable", "unavailable");
    private static GpuMemoryProbe.Sample baselineSample;
    private static GpuMemoryProbe.Sample latestSample;
    private static SnapshotData lastSnapshot;

    private GpuProfiler() {
    }

    public static boolean isActive() {
        return active;
    }

    public static StartResult start() {
        synchronized (LOCK) {
            if (active) {
                return new StartResult(false, "A WO VRAM profiling session is already running.");
            }

            RESOURCES.clear();
            MOD_CACHE.clear();
            SAMPLES.clear();
            SNAPSHOTS.clear();
            allocationEvents = 0L;
            deletionEvents = 0L;
            allocatedBytes = 0L;
            releasedBytes = 0L;
            hookErrors = 0;
            startedAt = Instant.now();
            stoppedAt = null;
            startedNanos = System.nanoTime();
            nextSampleNanos = startedNanos;
            gpuInfo = GpuMemoryProbe.gpuInfo();
            baselineSample = null;
            latestSample = null;
            active = true;
            GpuDiagnostics.start();
            sampleLocked(startedNanos);
            lastSnapshot = createSnapshotLocked("start");
            SNAPSHOTS.add(lastSnapshot);

            String provider = baselineSample != null ? baselineSample.provider() : "unavailable";
            return new StartResult(true, "WO VRAM profiler started (hardware counter: " + provider + ").");
        }
    }

    public static StopResult stop() {
        synchronized (LOCK) {
            if (!active) {
                return new StopResult(false, "No WO VRAM profiling session is running.", statusLocked());
            }
            sampleLocked(System.nanoTime());
            GpuDiagnostics.stop();
            active = false;
            stoppedAt = Instant.now();
            cachedDebugLines = List.of();
            return new StopResult(true, "WO VRAM profiler stopped.", statusLocked());
        }
    }

    public static void onFrame() {
        if (!active) {
            return;
        }
        long now = System.nanoTime();
        if (now >= nextSampleNanos) {
            synchronized (LOCK) {
                if (active && now >= nextSampleNanos) {
                    sampleLocked(now);
                }
            }
        }
        GpuDiagnostics.onFrame();
    }

    public static void onTextureStorage(int target, int level, int internalFormat, int width, int height, int format, int type) {
        if (!active) {
            return;
        }
        try {
            int textureId = boundTexture(target);
            long bytes = GpuFormatEstimator.textureBytes(internalFormat, width, height, format, type);
            recordAllocation(ResourceType.TEXTURE, textureId, level, bytes,
                    width + "x" + height + " level " + level + " internal=0x" + Integer.toHexString(internalFormat));
        } catch (Throwable ignored) {
            recordHookError();
        }
    }

    public static void onBufferStorage(int target, ByteBuffer data) {
        onBufferStorage(target, data == null ? 0L : data.remaining());
    }

    public static void onBufferStorage(int target, long size) {
        if (!active) {
            return;
        }
        try {
            int bufferId = boundBuffer(target);
            recordAllocation(ResourceType.BUFFER, bufferId, 0, Math.max(0L, size),
                    "target=0x" + Integer.toHexString(target));
        } catch (Throwable ignored) {
            recordHookError();
        }
    }

    public static void onRenderbufferStorage(int internalFormat, int width, int height) {
        if (!active) {
            return;
        }
        try {
            int renderbufferId = GL11.glGetInteger(36007); // GL_RENDERBUFFER_BINDING
            long bytes = GpuFormatEstimator.renderbufferBytes(internalFormat, width, height);
            recordAllocation(ResourceType.RENDERBUFFER, renderbufferId, 0, bytes,
                    width + "x" + height + " internal=0x" + Integer.toHexString(internalFormat));
        } catch (Throwable ignored) {
            recordHookError();
        }
    }

    public static void onTextureDeleted(int textureId) {
        deleteResource(ResourceType.TEXTURE, textureId);
    }

    public static void onTexturesDeleted(int[] textureIds) {
        if (textureIds == null) {
            return;
        }
        for (int textureId : textureIds) {
            deleteResource(ResourceType.TEXTURE, textureId);
        }
    }

    public static void onBufferDeleted(int bufferId) {
        deleteResource(ResourceType.BUFFER, bufferId);
    }

    public static void onRenderbufferDeleted(int renderbufferId) {
        deleteResource(ResourceType.RENDERBUFFER, renderbufferId);
    }

    public static void labelTexture(int textureId, ResourceLocation resource) {
        if (!active || textureId <= 0 || resource == null) {
            return;
        }
        synchronized (LOCK) {
            TrackedResource tracked = RESOURCES.get(new ResourceKey(ResourceType.TEXTURE, textureId));
            if (tracked != null) {
                tracked.label = resource.toString();
            }
        }
    }

    public static void recordAtlasContributors(int textureId, ResourceLocation atlas,
                                               Map<ResourceLocation, TextureAtlasSprite> sprites) {
        if (!active || textureId <= 0 || sprites == null || sprites.isEmpty()) {
            return;
        }
        synchronized (LOCK) {
            TrackedResource tracked = RESOURCES.get(new ResourceKey(ResourceType.TEXTURE, textureId));
            if (tracked == null) {
                return;
            }
            tracked.label = atlas == null ? tracked.label : atlas.toString();
            Map<String, Long> weights = new TreeMap<>();
            for (Map.Entry<ResourceLocation, TextureAtlasSprite> entry : sprites.entrySet()) {
                try {
                    long area = Math.max(1L, (long) entry.getValue().contents().width() * entry.getValue().contents().height());
                    weights.merge(entry.getKey().getNamespace(), area, GpuProfiler::saturatedAdd);
                } catch (Throwable ignored) {
                    // A single malformed sprite must not discard the rest of the atlas report.
                }
            }
            tracked.contributorWeights = Map.copyOf(weights);
        }
    }

    public static Status status() {
        synchronized (LOCK) {
            return statusLocked();
        }
    }

    public static SnapshotResult snapshot(String name) {
        synchronized (LOCK) {
            if (startedAt == null) {
                return new SnapshotResult(false, "Start a WO VRAM profiling session first.", null);
            }
            String normalized = name == null || name.isBlank() ? "snapshot-" + SNAPSHOTS.size() : name.trim();
            SnapshotData snapshot = createSnapshotLocked(normalized);
            SNAPSHOTS.add(snapshot);
            lastSnapshot = snapshot;
            return new SnapshotResult(true, "Captured VRAM snapshot '" + normalized + "'.", normalized);
        }
    }

    public static List<SiteSummary> top(int limit) {
        synchronized (LOCK) {
            return aggregateLocked().values().stream()
                    .map(Aggregate::toSummary)
                    .sorted(Comparator.comparingLong(SiteSummary::bytes).reversed())
                    .limit(Math.max(1, limit))
                    .toList();
        }
    }

    public static DiffResult diff(int limit) {
        synchronized (LOCK) {
            if (lastSnapshot == null) {
                return new DiffResult("none", List.of());
            }
            Map<SiteKey, Aggregate> current = aggregateLocked();
            Set<SiteKey> keys = new HashSet<>(current.keySet());
            keys.addAll(lastSnapshot.bySite.keySet());
            List<SiteSummary> rows = new ArrayList<>();
            for (SiteKey key : keys) {
                Aggregate now = current.get(key);
                Aggregate before = lastSnapshot.bySite.get(key);
                long bytes = (now == null ? 0L : now.bytes) - (before == null ? 0L : before.bytes);
                int objects = (now == null ? 0 : now.objects) - (before == null ? 0 : before.objects);
                if (bytes != 0L || objects != 0) {
                    rows.add(new SiteSummary(key.modId, key.label, key.className, key.methodName,
                            key.fileName, key.lineNumber, bytes, objects));
                }
            }
            rows.sort(Comparator.comparingLong((SiteSummary row) -> absoluteForSort(row.bytes())).reversed());
            return new DiffResult(lastSnapshot.name, rows.stream().limit(Math.max(1, limit)).toList());
        }
    }

    /**
     * Exports the complete JSON report and a sibling flamegraph-compatible folded stack file.
     */
    public static Path export(Path directory) throws IOException {
        Map<String, Object> report;
        List<String> foldedStackLines;
        String filename;
        synchronized (LOCK) {
            if (startedAt == null) {
                throw new IllegalStateException("Start a WO VRAM profiling session first.");
            }
            report = buildReportLocked();
            foldedStackLines = GpuDiagnostics.foldedStackLines();
            filename = "gpu-profile-" + FILE_TIME.format(startedAt) + ".json";
        }

        Files.createDirectories(directory);
        Path output = directory.resolve(filename);
        Path foldedOutput = directory.resolve(filename.replace(".json", ".folded"));
        Files.writeString(output, GSON.toJson(report), StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
        Files.write(foldedOutput, foldedStackLines, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
        return output.toAbsolutePath().normalize();
    }

    public static List<String> debugLines() {
        if (!active) {
            return List.of();
        }
        List<String> lines = new ArrayList<>(cachedDebugLines);
        lines.addAll(GpuDiagnostics.debugLines());
        return List.copyOf(lines);
    }

    static DiagnosticAttribution captureDiagnosticAttribution() {
        AllocationSite site = captureSite();
        return new DiagnosticAttribution(site.modId, site.label == null ? "" : site.label,
                site.className, site.methodName, site.fileName, site.lineNumber, site.stack);
    }

    public static String formatBytes(long bytes) {
        boolean negative = bytes < 0L;
        double value = Math.abs((double) bytes);
        String[] units = {"B", "KiB", "MiB", "GiB", "TiB"};
        int unit = 0;
        while (value >= 1024.0 && unit < units.length - 1) {
            value /= 1024.0;
            unit++;
        }
        String formatted = unit == 0 ? String.format("%.0f %s", value, units[unit]) : String.format("%.1f %s", value, units[unit]);
        return negative ? "-" + formatted : formatted;
    }

    private static void recordAllocation(ResourceType type, int id, int part, long bytes, String description) {
        if (id <= 0 || bytes < 0L) {
            return;
        }
        AllocationSite site = captureSite();
        long now = System.nanoTime();
        synchronized (LOCK) {
            if (!active) {
                return;
            }
            ResourceKey key = new ResourceKey(type, id);
            TrackedResource resource = RESOURCES.computeIfAbsent(key, ignored -> new TrackedResource(key, now));
            long oldBytes = resource.parts.getOrDefault(part, 0L);
            resource.parts.put(part, bytes);
            resource.lastChangedNanos = now;
            resource.description = description;
            if (resource.site == null || part == 0) {
                resource.site = site;
            }
            if (resource.label == null && site.label != null) {
                resource.label = site.label;
            }
            long delta = bytes - oldBytes;
            if (delta > 0L) {
                allocatedBytes = saturatedAdd(allocatedBytes, delta);
            } else if (delta < 0L) {
                releasedBytes = saturatedAdd(releasedBytes, -delta);
            }
            allocationEvents++;
        }
    }

    private static void deleteResource(ResourceType type, int id) {
        if (!active || id <= 0) {
            return;
        }
        synchronized (LOCK) {
            if (!active) {
                return;
            }
            TrackedResource removed = RESOURCES.remove(new ResourceKey(type, id));
            if (removed != null) {
                releasedBytes = saturatedAdd(releasedBytes, removed.totalBytes());
            }
            deletionEvents++;
        }
    }

    private static void sampleLocked(long now) {
        long elapsed = Math.max(0L, now - startedNanos);
        GpuMemoryProbe.Sample sample = GpuMemoryProbe.sample(elapsed);
        latestSample = sample;
        if (baselineSample == null) {
            baselineSample = sample;
        }
        if (SAMPLES.size() >= MAX_SAMPLES) {
            SAMPLES.remove(1);
        }
        SAMPLES.add(sample);
        nextSampleNanos = now + SAMPLE_INTERVAL_NANOS;
        cachedDebugLines = buildDebugLinesLocked();
    }

    private static List<String> buildDebugLinesLocked() {
        Status status = statusLocked();
        List<String> lines = new ArrayList<>();
        String hardware = status.hardwareDeltaBytes == null ? "counter unavailable" : signedBytes(status.hardwareDeltaBytes);
        lines.add("[WO VRAM] driver " + hardware + " | tracked " + formatBytes(status.trackedBytes)
                + " / " + status.resourceCount + " objects");
        aggregateLocked().values().stream()
                .map(Aggregate::toSummary)
                .sorted(Comparator.comparingLong(SiteSummary::bytes).reversed())
                .limit(3)
                .forEach(row -> lines.add("  " + row.modId + " " + formatBytes(row.bytes) + " @ " + row.location()));
        return List.copyOf(lines);
    }

    private static Status statusLocked() {
        long now = active ? System.nanoTime() : startedNanos;
        if (!active && startedAt != null && stoppedAt != null) {
            now = startedNanos + Duration.between(startedAt, stoppedAt).toNanos();
        }
        long tracked = RESOURCES.values().stream().mapToLong(TrackedResource::totalBytes).reduce(0L, GpuProfiler::saturatedAdd);
        Long hardwareDelta = hardwareDeltaLocked();
        String provider = latestSample == null ? "unavailable" : latestSample.provider();
        return new Status(active, startedAt != null, Math.max(0L, now - startedNanos), tracked, RESOURCES.size(),
                allocationEvents, deletionEvents, allocatedBytes, releasedBytes, provider, hardwareDelta,
                gpuInfo.vendor(), gpuInfo.renderer(), lastSnapshot == null ? "none" : lastSnapshot.name, hookErrors);
    }

    private static Long hardwareDeltaLocked() {
        if (baselineSample == null || latestSample == null) {
            return null;
        }
        if (baselineSample.usedBytes() >= 0L && latestSample.usedBytes() >= 0L) {
            return latestSample.usedBytes() - baselineSample.usedBytes();
        }
        if (baselineSample.availableBytes() >= 0L && latestSample.availableBytes() >= 0L) {
            return baselineSample.availableBytes() - latestSample.availableBytes();
        }
        return null;
    }

    private static SnapshotData createSnapshotLocked(String name) {
        Map<SiteKey, Aggregate> current = aggregateLocked();
        Map<SiteKey, Aggregate> copied = new HashMap<>();
        current.forEach((key, value) -> copied.put(key, value.copy()));
        long tracked = RESOURCES.values().stream().mapToLong(TrackedResource::totalBytes).reduce(0L, GpuProfiler::saturatedAdd);
        return new SnapshotData(name, Math.max(0L, System.nanoTime() - startedNanos), tracked, hardwareDeltaLocked(), Map.copyOf(copied));
    }

    private static Map<SiteKey, Aggregate> aggregateLocked() {
        Map<SiteKey, Aggregate> result = new HashMap<>();
        for (TrackedResource resource : RESOURCES.values()) {
            long totalBytes = resource.totalBytes();
            if (totalBytes <= 0L || resource.site == null) {
                continue;
            }
            if (resource.contributorWeights == null || resource.contributorWeights.isEmpty()) {
                addAggregate(result, resource.site.modId, resource, totalBytes);
                continue;
            }

            long totalWeight = resource.contributorWeights.values().stream().reduce(0L, GpuProfiler::saturatedAdd);
            long assigned = 0L;
            int index = 0;
            for (Map.Entry<String, Long> contribution : resource.contributorWeights.entrySet()) {
                index++;
                long share = index == resource.contributorWeights.size()
                        ? Math.max(0L, totalBytes - assigned)
                        : proportionalShare(totalBytes, contribution.getValue(), totalWeight);
                assigned = saturatedAdd(assigned, share);
                addAggregate(result, contribution.getKey(), resource, share);
            }
        }
        return result;
    }

    private static void addAggregate(Map<SiteKey, Aggregate> result, String modId, TrackedResource resource, long bytes) {
        AllocationSite site = resource.site;
        SiteKey key = new SiteKey(modId == null ? "unknown" : modId,
                resource.label == null ? resource.key.type.name().toLowerCase() + "#" + resource.key.id : resource.label,
                site.className, site.methodName, site.fileName, site.lineNumber);
        result.computeIfAbsent(key, ignored -> new Aggregate(key)).add(bytes, 1);
    }

    private static AllocationSite captureSite() {
        ResourceLocation context = GpuProfilerContext.currentResource();
        List<StackWalker.StackFrame> frames = STACK_WALKER.walk(stream -> stream
                .filter(frame -> !isNoiseFrame(frame.getClassName()))
                .limit(32)
                .toList());
        StackWalker.StackFrame primary = frames.stream().filter(frame -> isExternalFrame(frame.getClassName())).findFirst()
                .orElseGet(() -> frames.stream().findFirst().orElse(null));

        String modId = primary == null ? "unknown" : resolveMod(primary.getDeclaringClass());
        if (context != null && ("unknown".equals(modId) || "minecraft".equals(modId))) {
            String namespace = context.getNamespace();
            if (!"minecraft".equals(namespace) && isLoadedMod(namespace)) {
                modId = namespace;
            }
        }

        String className = primary == null ? "unknown" : primary.getClassName();
        String methodName = primary == null ? "unknown" : primary.getMethodName();
        String fileName = primary == null || primary.getFileName() == null ? "unknown" : primary.getFileName();
        int line = primary == null ? -1 : primary.getLineNumber();
        List<String> stack = frames.stream().map(GpuProfiler::formatFrame).toList();
        return new AllocationSite(modId, className, methodName, fileName, line,
                context == null ? null : context.toString(), stack);
    }

    private static String resolveMod(Class<?> type) {
        synchronized (LOCK) {
            String cached = MOD_CACHE.get(type);
            if (cached != null) {
                return cached;
            }
        }

        String className = type.getName();
        String resolved = "unknown";
        if (className.startsWith("net.minecraft.") || className.startsWith("com.mojang.")) {
            resolved = "minecraft";
        } else if (className.startsWith("com.thunder.wildernessodysseyapi.")) {
            resolved = ModConstants.MOD_ID;
        } else {
            try {
                String moduleName = type.getModule().getName();
                Path sourcePath = codeSourcePath(type);
                for (IModInfo mod : ModList.get().getMods()) {
                    if (moduleName != null && moduleName.equals(mod.getOwningFile().moduleName())) {
                        resolved = mod.getModId();
                        break;
                    }
                    Path modPath = mod.getOwningFile().getFile().getFilePath().toAbsolutePath().normalize();
                    if (sourcePath != null && (sourcePath.equals(modPath) || sourcePath.startsWith(modPath) || modPath.startsWith(sourcePath))) {
                        resolved = mod.getModId();
                        break;
                    }
                }
            } catch (Throwable ignored) {
                // Fall through to unknown; attribution should never break an allocation.
            }
        }

        synchronized (LOCK) {
            MOD_CACHE.put(type, resolved);
        }
        return resolved;
    }

    private static Path codeSourcePath(Class<?> type) {
        try {
            CodeSource source = type.getProtectionDomain().getCodeSource();
            if (source == null || source.getLocation() == null) {
                return null;
            }
            URI uri = source.getLocation().toURI();
            return "file".equalsIgnoreCase(uri.getScheme()) ? Path.of(uri).toAbsolutePath().normalize() : null;
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static boolean isLoadedMod(String modId) {
        try {
            return ModList.get().isLoaded(modId);
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static boolean isNoiseFrame(String className) {
        return className.startsWith("java.")
                || className.startsWith("jdk.")
                || className.startsWith("org.lwjgl.")
                || className.startsWith("org.spongepowered.")
                || className.startsWith("net.neoforged.")
                || className.startsWith("com.thunder.wildernessodysseyapi.gpuprofiler.")
                || className.endsWith("GlStateManagerGpuProfilerMixin")
                || className.endsWith("RenderSystemGpuDiagnosticsMixin");
    }

    private static boolean isExternalFrame(String className) {
        return !className.startsWith("net.minecraft.")
                && !className.startsWith("com.mojang.")
                && !isNoiseFrame(className);
    }

    private static String formatFrame(StackWalker.StackFrame frame) {
        return frame.getClassName() + "." + frame.getMethodName() + "(" +
                (frame.getFileName() == null ? "Unknown Source" : frame.getFileName() + ":" + frame.getLineNumber()) + ")";
    }

    private static int boundTexture(int target) {
        int binding = switch (target) {
            case 34067, 34069, 34070, 34071, 34072, 34073, 34074 -> 34068; // cube map
            case 35866 -> 35869; // 2D array
            case 32879 -> 32874; // 3D
            case 3552 -> 32872; // 1D
            default -> 32873; // 2D
        };
        return GL11.glGetInteger(binding);
    }

    private static int boundBuffer(int target) {
        int binding = switch (target) {
            case 34963 -> 34965; // ELEMENT_ARRAY_BUFFER_BINDING
            case 35051 -> 35053; // PIXEL_PACK_BUFFER_BINDING
            case 35052 -> 35055; // PIXEL_UNPACK_BUFFER_BINDING
            case 35345 -> 35368; // UNIFORM_BUFFER_BINDING
            case 36662 -> 36662; // COPY_READ_BUFFER_BINDING
            case 36663 -> 36663; // COPY_WRITE_BUFFER_BINDING
            case 35882 -> 35885; // TEXTURE_BUFFER_BINDING
            case 35982 -> 35983; // TRANSFORM_FEEDBACK_BUFFER_BINDING
            case 36671 -> 36675; // DRAW_INDIRECT_BUFFER_BINDING
            case 37074 -> 37075; // SHADER_STORAGE_BUFFER_BINDING
            case 37102 -> 37103; // DISPATCH_INDIRECT_BUFFER_BINDING
            case 37266 -> 37267; // QUERY_BUFFER_BINDING
            case 37568 -> 37569; // ATOMIC_COUNTER_BUFFER_BINDING
            default -> 34964; // ARRAY_BUFFER_BINDING
        };
        return GL11.glGetInteger(binding);
    }

    private static Map<String, Object> buildReportLocked() {
        Status status = statusLocked();
        Map<String, Object> report = new LinkedHashMap<>();
        report.put("schemaVersion", 2);
        report.put("coverage", "Minecraft/Mojang allocation paths plus sampled RenderSystem draw timing, named WO scopes, KHR_debug messages, and scoped state-leak checks; raw-LWJGL draws and shared vanilla batches may be unattributed");
        report.put("estimateNotice", "Tracked byte counts are logical allocation estimates; driver compression, alignment, sharing, and delayed release can differ");
        report.put("session", Map.of(
                "startedAt", startedAt.toString(),
                "stoppedAt", stoppedAt == null ? "" : stoppedAt.toString(),
                "active", active,
                "elapsedNanos", status.elapsedNanos,
                "hardwareProvider", status.hardwareProvider
        ));
        report.put("gpu", Map.of("vendor", gpuInfo.vendor(), "renderer", gpuInfo.renderer(), "version", gpuInfo.version()));

        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("trackedBytes", status.trackedBytes);
        summary.put("hardwareDeltaBytes", status.hardwareDeltaBytes);
        summary.put("liveResources", status.resourceCount);
        summary.put("allocationEvents", allocationEvents);
        summary.put("deletionEvents", deletionEvents);
        summary.put("allocatedBytes", allocatedBytes);
        summary.put("releasedBytes", releasedBytes);
        summary.put("hookErrors", hookErrors);
        report.put("summary", summary);

        report.put("samples", SAMPLES.stream().map(sample -> {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("elapsedNanos", sample.elapsedNanos());
            row.put("provider", sample.provider());
            row.put("totalBytes", sample.totalBytes());
            row.put("availableBytes", sample.availableBytes());
            row.put("usedBytes", sample.usedBytes());
            return row;
        }).toList());

        report.put("topSites", aggregateLocked().values().stream().map(Aggregate::toSummary)
                .sorted(Comparator.comparingLong(SiteSummary::bytes).reversed()).toList());
        report.put("resources", RESOURCES.values().stream()
                .sorted(Comparator.comparingLong(TrackedResource::totalBytes).reversed())
                .map(TrackedResource::toReportRow).toList());
        report.put("snapshots", SNAPSHOTS.stream().map(SnapshotData::toReportRow).toList());
        report.put("diagnostics", GpuDiagnostics.reportData());
        return report;
    }

    private static void recordHookError() {
        synchronized (LOCK) {
            hookErrors++;
        }
    }

    private static long proportionalShare(long total, long weight, long totalWeight) {
        if (total <= 0L || weight <= 0L || totalWeight <= 0L) {
            return 0L;
        }
        return Math.min(total, Math.round((double) total * (double) weight / (double) totalWeight));
    }

    private static long saturatedAdd(long left, long right) {
        if (right > 0L && left > Long.MAX_VALUE - right) {
            return Long.MAX_VALUE;
        }
        return left + right;
    }

    private static long absoluteForSort(long value) {
        return value == Long.MIN_VALUE ? Long.MAX_VALUE : Math.abs(value);
    }

    private static String signedBytes(long bytes) {
        return (bytes >= 0L ? "+" : "") + formatBytes(bytes);
    }

    private enum ResourceType {
        TEXTURE,
        BUFFER,
        RENDERBUFFER
    }

    private record ResourceKey(ResourceType type, int id) {
    }

    private record AllocationSite(String modId, String className, String methodName, String fileName,
                                  int lineNumber, String label, List<String> stack) {
    }

    private record SiteKey(String modId, String label, String className, String methodName,
                           String fileName, int lineNumber) {
    }

    private static final class TrackedResource {
        private final ResourceKey key;
        private final long firstSeenNanos;
        private final Map<Integer, Long> parts = new TreeMap<>();
        private long lastChangedNanos;
        private String description;
        private String label;
        private AllocationSite site;
        private Map<String, Long> contributorWeights = Map.of();

        private TrackedResource(ResourceKey key, long now) {
            this.key = key;
            this.firstSeenNanos = now;
            this.lastChangedNanos = now;
        }

        private long totalBytes() {
            return this.parts.values().stream().reduce(0L, GpuProfiler::saturatedAdd);
        }

        private Map<String, Object> toReportRow() {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("type", this.key.type.name());
            row.put("id", this.key.id);
            row.put("label", this.label == null ? "" : this.label);
            row.put("description", this.description == null ? "" : this.description);
            row.put("estimatedBytes", this.totalBytes());
            row.put("parts", this.parts);
            row.put("contributorWeights", this.contributorWeights);
            row.put("firstSeenNanos", Math.max(0L, this.firstSeenNanos - startedNanos));
            row.put("lastChangedNanos", Math.max(0L, this.lastChangedNanos - startedNanos));
            if (this.site != null) {
                row.put("modId", this.site.modId);
                row.put("className", this.site.className);
                row.put("methodName", this.site.methodName);
                row.put("fileName", this.site.fileName);
                row.put("lineNumber", this.site.lineNumber);
                row.put("stack", this.site.stack);
            }
            return row;
        }
    }

    private static final class Aggregate {
        private final SiteKey key;
        private long bytes;
        private int objects;

        private Aggregate(SiteKey key) {
            this.key = key;
        }

        private void add(long addedBytes, int addedObjects) {
            this.bytes = saturatedAdd(this.bytes, addedBytes);
            this.objects += addedObjects;
        }

        private Aggregate copy() {
            Aggregate copy = new Aggregate(this.key);
            copy.bytes = this.bytes;
            copy.objects = this.objects;
            return copy;
        }

        private SiteSummary toSummary() {
            return new SiteSummary(this.key.modId, this.key.label, this.key.className, this.key.methodName,
                    this.key.fileName, this.key.lineNumber, this.bytes, this.objects);
        }
    }

    private record SnapshotData(String name, long elapsedNanos, long trackedBytes, Long hardwareDeltaBytes,
                                Map<SiteKey, Aggregate> bySite) {
        private Map<String, Object> toReportRow() {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("name", this.name);
            row.put("elapsedNanos", this.elapsedNanos);
            row.put("trackedBytes", this.trackedBytes);
            row.put("hardwareDeltaBytes", this.hardwareDeltaBytes);
            row.put("sites", this.bySite.values().stream().map(Aggregate::toSummary)
                    .sorted(Comparator.comparingLong(SiteSummary::bytes).reversed()).toList());
            return row;
        }
    }

    public record StartResult(boolean started, String message) {
    }

    public record StopResult(boolean stopped, String message, Status status) {
    }

    public record SnapshotResult(boolean captured, String message, String name) {
    }

    public record DiffResult(String snapshotName, List<SiteSummary> rows) {
    }

    public record Status(boolean active, boolean hasSession, long elapsedNanos, long trackedBytes, int resourceCount,
                         long allocationEvents, long deletionEvents, long allocatedBytes, long releasedBytes,
                         String hardwareProvider, Long hardwareDeltaBytes, String gpuVendor, String gpuRenderer,
                         String lastSnapshotName, int hookErrors) {
    }

    public record SiteSummary(String modId, String label, String className, String methodName, String fileName,
                              int lineNumber, long bytes, int objects) {
        public String location() {
            String file = this.fileName == null || this.fileName.equals("unknown") ? simpleClassName(this.className) : this.fileName;
            return this.lineNumber > 0 ? file + ":" + this.lineNumber : file;
        }

        private static String simpleClassName(String className) {
            int separator = className == null ? -1 : className.lastIndexOf('.');
            return separator >= 0 ? className.substring(separator + 1) : String.valueOf(className);
        }
    }

    record DiagnosticAttribution(String modId, String label, String className, String methodName,
                                 String fileName, int lineNumber, List<String> stack) {
        String location() {
            return this.lineNumber > 0 ? this.fileName + ":" + this.lineNumber : this.fileName;
        }
    }
}
