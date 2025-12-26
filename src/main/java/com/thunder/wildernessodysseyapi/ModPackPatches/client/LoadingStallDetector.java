package com.thunder.wildernessodysseyapi.ModPackPatches.client;

import com.thunder.ticktoklib.TickTokHelper;
import com.thunder.wildernessodysseyapi.Core.ModConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.LoadingOverlay;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalLong;

/**
 * Watches the loading overlay and emits a detailed thread + mod snapshot
 * if it stays visible for an extended period (e.g., long modpack hangs).
 */
@EventBusSubscriber(modid = ModConstants.MOD_ID, value = Dist.CLIENT)
public final class LoadingStallDetector {
    private static final Duration STALL_THRESHOLD = Duration.ofMinutes(resolveThresholdMinutes());
    private static final Duration REMINDER_INTERVAL = Duration.ofMinutes(1);
    private static final DateTimeFormatter TIMESTAMP = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final long DEFAULT_THRESHOLD_MINUTES = TickTokHelper.duration(0,5,0,0);

    private static long overlayStartedAt = 0L;
    private static long lastProgressAt = 0L;
    private static long lastReportAt = 0L;

    private LoadingStallDetector() {
    }

    /**
     * Called from the loading overlay mixin every render; marks that we are
     * still making progress and (if first seen) arms the detector.
     */
    public static void recordProgress() {
        long now = System.currentTimeMillis();
        if (overlayStartedAt == 0L) {
            overlayStartedAt = now;
            ModConstants.LOGGER.info("Loading stall detector armed (loading overlay detected).");
        }
        lastProgressAt = now;
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Pre event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null) {
            reset();
            return;
        }

        boolean onLoadingOverlay = minecraft.getOverlay() instanceof LoadingOverlay;
        long now = System.currentTimeMillis();

        if (!onLoadingOverlay) {
            if (overlayStartedAt != 0L) {
                ModConstants.LOGGER.debug("Loading stall detector disarmed (overlay closed).");
            }
            reset();
            return;
        }

        if (overlayStartedAt == 0L) {
            overlayStartedAt = now;
            lastProgressAt = now;
        }

        boolean pastThreshold = now - overlayStartedAt >= STALL_THRESHOLD.toMillis();
        boolean remindIntervalElapsed = now - lastReportAt >= REMINDER_INTERVAL.toMillis();

        if (pastThreshold && remindIntervalElapsed) {
            lastReportAt = now;
            writeStallReport(now);
        }
    }

    private static void writeStallReport(long now) {
        Path reportDir = Paths.get("logs", "loading-stalls");
        String filename = "loading-stall-" + DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss").format(LocalDateTime.now()) + ".log";
        Path reportFile = reportDir.resolve(filename);

        try {
            Files.createDirectories(reportDir);
            String report = buildReport(now);
            Files.writeString(reportFile, report);
            ModConstants.LOGGER.warn("Loading screen has been visible for {} minutes. Wrote stall snapshot to {}",
                    STALL_THRESHOLD.toMinutes(), reportFile.toAbsolutePath());
        } catch (IOException ioException) {
            ModConstants.LOGGER.error("Failed to write loading stall report", ioException);
        }
    }

    private static String buildReport(long now) {
        StringBuilder builder = new StringBuilder();
        builder.append("=== Loading Stall Snapshot (").append(TIMESTAMP.format(LocalDateTime.now())).append(") ===\n");
        builder.append("Overlay duration: ").append(formatDuration(now - overlayStartedAt)).append('\n');
        builder.append("Time since last overlay render: ").append(formatDuration(now - lastProgressAt)).append('\n');
        builder.append("Mod count: ").append(ModList.get().size()).append('\n');

        appendModList(builder);
        appendThreadDump(builder);

        return builder.toString();
    }

    private static void appendModList(StringBuilder builder) {
        List<String> mods = ModList.get().getMods().stream()
                .map(mod -> mod.getModId() + "@" + mod.getVersion())
                .sorted()
                .toList();

        builder.append("\n[Loaded Mods]\n");
        mods.forEach(mod -> builder.append(" - ").append(mod).append('\n'));
    }

    private static void appendThreadDump(StringBuilder builder) {
        builder.append("\n[Thread Dump]\n");
        Map<Thread, StackTraceElement[]> traces = Thread.getAllStackTraces();

        appendThreadSuspects(builder, traces);

        traces.entrySet().stream()
                .sorted(Map.Entry.comparingByKey(Comparator.comparing(Thread::getName)))
                .forEach(entry -> {
                    Thread thread = entry.getKey();
                    StackTraceElement[] stack = entry.getValue();

                    builder.append("\nThread: ").append(thread.getName())
                            .append(" (id=").append(thread.getId()).append(", state=").append(thread.getState()).append(")\n");

                    for (int i = 0; i < stack.length; i++) {
                        builder.append("    at ").append(stack[i]).append('\n');
                        if (i >= 31) {
                            builder.append("    ... ").append(stack.length - 32).append(" more\n");
                            break;
                        }
                    }
                });

        builder.append("\n");
    }

    /**
     * Emits a lightweight suspect list before the full dump, prioritizing threads that are
     * RUNNABLE or BLOCKED (often the ones wedging the loader).
     */
    private static void appendThreadSuspects(StringBuilder builder, Map<Thread, StackTraceElement[]> traces) {
        builder.append("[Likely Culprit Threads]\n");
        traces.entrySet().stream()
                .sorted((a, b) -> threadPriority(b.getKey()) - threadPriority(a.getKey()))
                .limit(6)
                .forEach(entry -> {
                    Thread thread = entry.getKey();
                    StackTraceElement[] stack = entry.getValue();
                    builder.append(" - ")
                            .append(thread.getName())
                            .append(" (state=")
                            .append(thread.getState())
                            .append(")");

                    resolveJar(stack).ifPresent(jar -> builder.append(" [jar=").append(jar).append("]"));

                    if (stack.length > 0) {
                        builder.append(" at ").append(stack[0]);
                    }
                    builder.append('\n');
                });
        builder.append('\n');
    }

    private static int threadPriority(Thread thread) {
        // Prefer threads that are RUNNABLE/BLOCKED over waiting/parked ones.
        return switch (thread.getState()) {
            case RUNNABLE, BLOCKED -> 3;
            case WAITING, TIMED_WAITING -> 2;
            default -> 1;
        };
    }

    private static Optional<String> resolveJar(StackTraceElement[] stack) {
        if (stack.length == 0) {
            return Optional.empty();
        }
        String className = stack[0].getClassName();
        try {
            Class<?> cls = Class.forName(className, false, Thread.currentThread().getContextClassLoader());
            if (cls.getProtectionDomain() != null && cls.getProtectionDomain().getCodeSource() != null) {
                var source = cls.getProtectionDomain().getCodeSource().getLocation();
                if (source != null) {
                    return Optional.of(source.getPath());
                }
            }
        } catch (ClassNotFoundException ignored) {
            // Best-effort only; fall back to the raw class name if unavailable.
        }
        return Optional.empty();
    }

    private static String formatDuration(long millis) {
        long safeMillis = Math.max(millis, 0);
        int ticks = toTicks(safeMillis);

        String base = ticks >= TickTokHelper.TICKS_PER_HOUR
                ? TickTokHelper.formatTicksToHMS(ticks)
                : TickTokHelper.formatTicksToMinSec(ticks);

        long remainderMillis = safeMillis % 1000;
        return base + String.format(".%03d", remainderMillis);
    }

    private static int toTicks(long millis) {
        long ticks = Math.round(millis / 50.0d);
        return (int) Math.min(ticks, Integer.MAX_VALUE);
    }

    private static long resolveThresholdMinutes() {
        String override = System.getProperty("wilderness.loadingstall.minutes", "").trim();
        if (override.isEmpty()) {
            return DEFAULT_THRESHOLD_MINUTES;
        }

        return parseThresholdOverride(override).orElse(DEFAULT_THRESHOLD_MINUTES);
    }

    private static OptionalLong parseThresholdOverride(String override) {
        try {
            if (override.endsWith("t")) { // ticks suffix
                int ticks = Integer.parseInt(override.substring(0, override.length() - 1));
                long minutes = Math.round(TickTokHelper.toMinutes(ticks));
                return minutes > 0 ? OptionalLong.of(minutes) : OptionalLong.empty();
            }

            if (override.contains(":")) { // mm:ss or hh:mm:ss
                long seconds = parseColonTimeSeconds(override);
                if (seconds > 0) {
                    long minutes = Math.max(1L, Math.round(seconds / 60.0d));
                    return OptionalLong.of(minutes);
                }
            }

            long minutes = Long.parseLong(override);
            return minutes > 0 ? OptionalLong.of(minutes) : OptionalLong.empty();
        } catch (NumberFormatException ignored) {
            return OptionalLong.empty();
        }
    }

    private static long parseColonTimeSeconds(String value) {
        String[] parts = value.split(":");
        if (parts.length < 2 || parts.length > 3) {
            return -1L;
        }
        try {
            long hours = parts.length == 3 ? Long.parseLong(parts[0]) : 0L;
            long minutes = Long.parseLong(parts[parts.length - 2]);
            long seconds = Long.parseLong(parts[parts.length - 1]);
            return hours * 3600L + minutes * 60L + seconds;
        } catch (NumberFormatException ignored) {
            return -1L;
        }
    }

    private static void reset() {
        overlayStartedAt = 0L;
        lastProgressAt = 0L;
        lastReportAt = 0L;
    }
}
