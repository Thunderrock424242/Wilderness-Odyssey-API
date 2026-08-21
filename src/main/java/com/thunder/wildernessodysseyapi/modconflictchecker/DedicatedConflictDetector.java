package com.thunder.wildernessodysseyapi.modconflictchecker;

import com.thunder.wildernessodysseyapi.modconflictchecker.util.LoggerUtil;
import net.neoforged.fml.ModList;
import net.neoforged.fml.loading.moddiscovery.ModFile;

import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.lang.management.ThreadInfo;
import java.lang.management.ThreadMXBean;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.jar.JarFile;
import java.util.zip.ZipEntry;

/**
 * Runs bounded, lifecycle-owned compatibility diagnostics.
 *
 * <p>The archive scan has hard entry and report limits so a very large mod pack cannot create an
 * unbounded map or log. The deadlock watchdog reports a thread only once per server lifecycle.</p>
 */
public final class DedicatedConflictDetector {

    private static final AtomicBoolean STARTED = new AtomicBoolean(false);
    private static final long DEADLOCK_SCAN_INTERVAL_SECONDS = 30;
    private static final int MAX_ARCHIVE_ENTRIES = 300_000;
    private static final int MAX_TRACKED_PATHS = 200_000;
    private static final int MAX_CONFLICT_REPORTS = 100;
    private static final int MAX_STACK_FRAMES = 64;
    private static final Set<Long> REPORTED_DEADLOCKS = new HashSet<>();
    private static ScheduledExecutorService executor;

    private DedicatedConflictDetector() {
        // Utility class
    }

    /**
     * Starts the async scanners for code conflicts and thread lockups.
     */
    public static synchronized void start() {
        if (!STARTED.compareAndSet(false, true)) {
            return;
        }

        executor = Executors.newSingleThreadScheduledExecutor(runnable -> {
            Thread thread = new Thread(runnable, "Wilderness-Compatibility-Diagnostics");
            thread.setDaemon(true);
            return thread;
        });
        executor.execute(DedicatedConflictDetector::scanArchives);
        executor.scheduleAtFixedRate(DedicatedConflictDetector::scanForDeadlocks,
                DEADLOCK_SCAN_INTERVAL_SECONDS,
                DEADLOCK_SCAN_INTERVAL_SECONDS,
                TimeUnit.SECONDS);
    }

    /**
     * Stops all diagnostic work and clears per-server report state.
     */
    public static synchronized void stop() {
        ScheduledExecutorService currentExecutor = executor;
        executor = null;
        STARTED.set(false);
        REPORTED_DEADLOCKS.clear();
        if (currentExecutor != null) {
            currentExecutor.shutdownNow();
        }
    }

    // Inspects every archive once and tracks only paths that can represent actual overlap.
    private static void scanArchives() {
        Map<String, String> classOwners = new HashMap<>();
        Map<String, String> shaderOwners = new HashMap<>();
        List<String> conflicts = new ArrayList<>();
        int inspectedEntries = 0;
        boolean truncated = false;

        archiveLoop:
        for (var mod : ModList.get().getMods()) {
            if ("neoforge".equals(mod.getModId())) {
                continue;
            }

            ModFile file = (ModFile) mod.getOwningFile().getFile();
            if (file == null || !file.getFilePath().toString().endsWith(".jar")) {
                continue;
            }

            try (JarFile jar = new JarFile(file.getFilePath().toFile())) {
                Enumeration<? extends ZipEntry> entries = jar.entries();

                while (entries.hasMoreElements()) {
                    if (Thread.currentThread().isInterrupted() || inspectedEntries >= MAX_ARCHIVE_ENTRIES) {
                        truncated = true;
                        break archiveLoop;
                    }

                    ZipEntry entry = entries.nextElement();
                    inspectedEntries++;
                    if (entry.isDirectory()) {
                        continue;
                    }

                    String name = entry.getName();
                    if (name.endsWith(".class")) {
                        trackOwner(classOwners, conflicts, "class", name, mod.getModId());
                    } else if (isShaderPath(name)) {
                        trackOwner(shaderOwners, conflicts, "shader", name, mod.getModId());
                    }
                    if (classOwners.size() + shaderOwners.size() >= MAX_TRACKED_PATHS
                            || conflicts.size() >= MAX_CONFLICT_REPORTS) {
                        truncated = true;
                        break archiveLoop;
                    }
                }
            } catch (IOException e) {
                LoggerUtil.log(LoggerUtil.ConflictSeverity.WARN,
                        "Could not inspect mod archive " + mod.getModId() + ": " + e.getMessage());
            }
        }

        conflicts.forEach(conflict -> LoggerUtil.log(LoggerUtil.ConflictSeverity.WARN, conflict));

        String qualifier = truncated ? "partial" : "complete";
        LoggerUtil.log(truncated ? LoggerUtil.ConflictSeverity.WARN : LoggerUtil.ConflictSeverity.INFO,
                "Compatibility archive scan " + qualifier + ": inspected " + inspectedEntries
                        + " entries and reported " + conflicts.size() + " overlapping paths.");
    }

    private static void trackOwner(Map<String, String> owners, List<String> conflicts, String type,
                                   String path, String modId) {
        String firstOwner = owners.putIfAbsent(path, modId);
        if (firstOwner != null && !firstOwner.equals(modId)) {
            conflicts.add("Overlapping " + type + " path '" + path + "' is provided by both '"
                    + firstOwner + "' and '" + modId + "'. Verify that the libraries are intentionally bundled.");
        }
    }

    private static boolean isShaderPath(String path) {
        return path.startsWith("assets/") && path.contains("/shaders/")
                && (path.endsWith(".vsh") || path.endsWith(".fsh") || path.endsWith(".json"));
    }

    // JVM deadlock detection is cheap, but a persistent deadlock must not flood the log every cycle.
    private static void scanForDeadlocks() {
        ThreadMXBean threadMXBean = ManagementFactory.getThreadMXBean();
        long[] deadlockedThreads = threadMXBean.findDeadlockedThreads();

        if (deadlockedThreads == null || deadlockedThreads.length == 0) {
            return;
        }

        ThreadInfo[] infos = threadMXBean.getThreadInfo(deadlockedThreads, true, true);
        for (ThreadInfo info : infos) {
            if (info == null) {
                continue;
            }
            synchronized (DedicatedConflictDetector.class) {
                if (!REPORTED_DEADLOCKS.add(info.getThreadId())) {
                    continue;
                }
            }

            StringBuilder builder = new StringBuilder();
            builder.append("Thread lockup detected: ")
                    .append(info.getThreadName())
                    .append(" (ID ")
                    .append(info.getThreadId())
                    .append(")").append(System.lineSeparator())
                    .append("  Lock owner: ")
                    .append(info.getLockOwnerName())
                    .append(" (ID ")
                    .append(info.getLockOwnerId())
                    .append(")").append(System.lineSeparator())
                    .append("  Lock info: ")
                    .append(info.getLockInfo()).append(System.lineSeparator())
                    .append("  Stack trace:");

            StackTraceElement[] stackTrace = info.getStackTrace();
            for (int index = 0; index < Math.min(stackTrace.length, MAX_STACK_FRAMES); index++) {
                builder.append(System.lineSeparator()).append("    at ").append(stackTrace[index]);
            }
            if (stackTrace.length > MAX_STACK_FRAMES) {
                builder.append(System.lineSeparator()).append("    ... ")
                        .append(stackTrace.length - MAX_STACK_FRAMES).append(" additional frames omitted");
            }

            LoggerUtil.log(LoggerUtil.ConflictSeverity.ERROR, builder.toString());
        }
    }
}
