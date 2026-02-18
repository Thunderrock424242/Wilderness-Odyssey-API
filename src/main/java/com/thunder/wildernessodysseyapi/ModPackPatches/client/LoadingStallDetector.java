package com.thunder.wildernessodysseyapi.ModPackPatches.client;

import com.thunder.ticktoklib.TickTokHelper;
import com.thunder.wildernessodysseyapi.core.ModConstants;
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
import java.util.OptionalInt;

/**
 * Watches the loading overlay and emits a detailed thread + mod snapshot
 * if it stays visible for an extended period (e.g., long modpack hangs).
 */
@EventBusSubscriber(modid = ModConstants.MOD_ID, value = Dist.CLIENT)
public final class LoadingStallDetector {
    private static final int DEFAULT_THRESHOLD_MINUTES = 5;
    private static final int STALL_THRESHOLD_MINUTES = resolveThresholdMinutes();
    private static final Duration STALL_THRESHOLD = toDuration(TickTokHelper.toTicksMinutes(STALL_THRESHOLD_MINUTES));
    private static final Duration REMINDER_INTERVAL = toDuration(TickTokHelper.toTicksMinutes(1));
    private static final DateTimeFormatter TIMESTAMP = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

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
                            .append(" (id=").append(thread.threadId()).append(", state=").append(thread.getState()).append(")\n");

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
        int ticks = safeMillisToTicks(safeMillis);

        String base = ticks >= TickTokHelper.TICKS_PER_HOUR
                ? formatTicksToHMS(ticks)
                : formatTicksToMinSec(ticks);

        long remainderMillis = safeMillis % 1000;
        return base + String.format(".%03d", remainderMillis);
    }

    private static int safeMillisToTicks(long millis) {
        double ticks = Math.round(Math.max(0L, millis) * TickTokHelper.TICKS_PER_SECOND / 1000.0d);
        return (int) Math.min(ticks, Integer.MAX_VALUE);
    }

    private static int resolveThresholdMinutes() {
        String override = System.getProperty("wilderness.loadingstall.minutes", "").trim();
        if (override.isEmpty()) {
            return DEFAULT_THRESHOLD_MINUTES;
        }

        return parseThresholdOverride(override).orElse(DEFAULT_THRESHOLD_MINUTES);
    }

    private static OptionalInt parseThresholdOverride(String override) {
        try {
            if (override.endsWith("t")) { // ticks suffix
                int ticks = Integer.parseInt(override.substring(0, override.length() - 1));
                int minutes = (int) Math.ceil(TickTokHelper.toMinutes(ticks));
                return minutes > 0 ? OptionalInt.of(minutes) : OptionalInt.empty();
            }

            if (override.contains(":")) { // mm:ss or hh:mm:ss
                long seconds = parseColonTimeSeconds(override);
                if (seconds > 0) {
                    int minutes = (int) Math.max(1L, Math.ceil(seconds / 60.0d));
                    return OptionalInt.of(minutes);
                }
            }

            long minutes = Long.parseLong(override);
            if (minutes > 0 && minutes <= Integer.MAX_VALUE) {
                return OptionalInt.of((int) minutes);
            }
        } catch (NumberFormatException ignored) {
            return OptionalInt.empty();
        }
        return OptionalInt.empty();
    }

    private static Duration toDuration(int ticks) {
        long millis = Math.round(TickTokHelper.toSeconds(ticks) * 1000.0d);
        return Duration.ofMillis(millis);
    }

    private static String formatTicksToHMS(int ticks) {
        long seconds = Math.max(0L, Math.round(TickTokHelper.toSeconds(ticks)));
        long hours = seconds / 3600;
        long minutes = (seconds % 3600) / 60;
        long remainderSeconds = seconds % 60;
        return String.format("%02d:%02d:%02d", hours, minutes, remainderSeconds);
    }

    private static String formatTicksToMinSec(int ticks) {
        long seconds = Math.max(0L, Math.round(TickTokHelper.toSeconds(ticks)));
        long minutes = seconds / 60;
        long remainderSeconds = seconds % 60;
        return String.format("%02d:%02d", minutes, remainderSeconds);
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
