package com.thunder.wildernessodysseyapi.ModPackPatches.ModConflictChecker;

import com.thunder.wildernessodysseyapi.ModPackPatches.ModConflictChecker.Util.LoggerUtil;
import net.neoforged.fml.ModList;
import net.neoforged.fml.loading.moddiscovery.ModFile;

import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.lang.management.ThreadInfo;
import java.lang.management.ThreadMXBean;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.jar.JarFile;
import java.util.zip.ZipEntry;

/**
 * A dedicated conflict detection utility that runs alongside the mod to surface
 * code-level conflicts and JVM thread lockups.
 */
public class DedicatedConflictDetector {

    private static final AtomicBoolean STARTED = new AtomicBoolean(false);
    private static final ScheduledExecutorService DEADLOCK_EXECUTOR = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread thread = new Thread(r, "Wilderness-Deadlock-Watcher");
        thread.setDaemon(true);
        return thread;
    });

    private static final long DEADLOCK_SCAN_INTERVAL_SECONDS = 30;

    private DedicatedConflictDetector() {
        // Utility class
    }

    /**
     * Starts the async scanners for code conflicts and thread lockups.
     */
    public static void start() {
        if (!STARTED.compareAndSet(false, true)) {
            return;
        }

        LoggerUtil.log(LoggerUtil.ConflictSeverity.INFO,
                "Starting dedicated conflict detector (class overlap + deadlock watchdog)...", false);
        LoggerUtil.log(LoggerUtil.ConflictSeverity.INFO,
                "Detailed results will be written to config/WildernessOdysseyAPI/conflict_log.txt", false);

        CompletableFuture.runAsync(DedicatedConflictDetector::scanForDuplicateClasses);
        DEADLOCK_EXECUTOR.scheduleAtFixedRate(DedicatedConflictDetector::scanForDeadlocks,
                DEADLOCK_SCAN_INTERVAL_SECONDS,
                DEADLOCK_SCAN_INTERVAL_SECONDS,
                TimeUnit.SECONDS);
    }

    private static void scanForDuplicateClasses() {
        Map<String, List<String>> classOwnership = new HashMap<>();

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
                    ZipEntry entry = entries.nextElement();
                    if (entry.isDirectory()) {
                        continue;
                    }

                    String name = entry.getName();
                    if (!name.endsWith(".class")) {
                        continue;
                    }

                    classOwnership.computeIfAbsent(name, k -> new ArrayList<>()).add(mod.getModId());
                }
            } catch (IOException e) {
                LoggerUtil.log(LoggerUtil.ConflictSeverity.WARN,
                        "Failed to inspect mod jar for class conflicts: " + mod.getModId() + " (" + e.getMessage() + ")", false);
            }
        }

        classOwnership.forEach((classPath, modIds) -> {
            if (modIds.size() > 1) {
                LoggerUtil.log(LoggerUtil.ConflictSeverity.ERROR,
                        String.format("Class conflict: '%s' is provided by multiple mods [%s]",
                                classPath, String.join(", ", modIds)), false);
            }
        });

        LoggerUtil.log(LoggerUtil.ConflictSeverity.INFO, "Class conflict scan completed.", false);
    }

    private static void scanForDeadlocks() {
        ThreadMXBean threadMXBean = ManagementFactory.getThreadMXBean();
        long[] deadlockedThreads = threadMXBean.findDeadlockedThreads();

        if (deadlockedThreads == null || deadlockedThreads.length == 0) {
            LoggerUtil.log(LoggerUtil.ConflictSeverity.INFO,
                    "Deadlock watcher: no deadlocked threads detected.", false);
            return;
        }

        ThreadInfo[] infos = threadMXBean.getThreadInfo(deadlockedThreads, true, true);
        for (ThreadInfo info : infos) {
            if (info == null) {
                continue;
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

            for (StackTraceElement element : info.getStackTrace()) {
                builder.append(System.lineSeparator()).append("    at ").append(element);
            }

            LoggerUtil.log(LoggerUtil.ConflictSeverity.ERROR, builder.toString(), false);
        }
    }
}
