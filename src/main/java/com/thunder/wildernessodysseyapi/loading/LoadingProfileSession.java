package com.thunder.wildernessodysseyapi.loading;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;

/**
 * Bounded, worker-owned history of one loading operation. Client observations
 * contain no world references. Stack ownership describes sampled call paths,
 * not exact per-mod CPU time or the cause of a blocked operation.
 */
public final class LoadingProfileSession {
    private static final int MAX_THREADS = 128;
    private static final int MAX_SITES = 256;
    private static final int MAX_PHASES = 16;
    private static final int MAX_STACK_DEPTH = 48;
    private final long startedNanos;
    private final Function<StackTraceElement, String> ownerResolver;
    private final Map<Long, ThreadHistory> threads = new LinkedHashMap<>();
    private final Map<Site, SiteHistory> sites = new LinkedHashMap<>();
    private final Map<String, Long> phases = new LinkedHashMap<>();
    private String phase = "Starting loading diagnostics";
    private int progress = -1;
    private long phaseStartedNanos;
    private long lastProgressNanos;
    private long lastClientNanos;
    private long samples;
    private long droppedThreads;
    private long droppedSites;

    public LoadingProfileSession(long startedNanos, Function<StackTraceElement, String> ownerResolver) {
        this.startedNanos = startedNanos;
        this.phaseStartedNanos = startedNanos;
        this.lastProgressNanos = startedNanos;
        this.lastClientNanos = startedNanos;
        this.ownerResolver = Objects.requireNonNull(ownerResolver);
    }

    /** A rendered frame is a heartbeat; only stage/percentage changes are progress. */
    public void observe(String phase, int progress, long nowNanos) {
        Objects.requireNonNull(phase);
        int boundedProgress = progress < 0 ? -1 : Math.min(100, progress);
        if (!this.phase.equals(phase)) {
            finishPhase(nowNanos);
            this.phase = phase;
            phaseStartedNanos = nowNanos;
            lastProgressNanos = nowNanos;
        } else if (this.progress != boundedProgress) {
            lastProgressNanos = nowNanos;
        }
        this.progress = boundedProgress;
        lastClientNanos = nowNanos;
    }

    /** Counts one bounded sampling pass; waiting stacks never count as runnable work. */
    public void sample(List<ThreadSample> observations) {
        samples++;
        for (ThreadSample sample : observations) {
            if (sample.name().startsWith("WO-Loading-Profiler")) {
                continue;
            }
            ThreadHistory thread = threads.get(sample.id());
            if (thread == null) {
                if (threads.size() >= MAX_THREADS) {
                    droppedThreads++;
                    continue;
                }
                thread = new ThreadHistory();
                threads.put(sample.id(), thread);
            }
            boolean runnable = sample.state() == Thread.State.RUNNABLE;
            if (runnable) {
                thread.runnable++;
            } else {
                thread.waiting++;
            }
            if (sample.cpuNanos() >= 0 && thread.lastCpu >= 0 && sample.cpuNanos() >= thread.lastCpu) {
                thread.cpu += sample.cpuNanos() - thread.lastCpu;
            }
            thread.lastCpu = sample.cpuNanos();
            thread.cpuAvailable |= sample.cpuNanos() >= 0;
            thread.latest = new ThreadSample(sample.id(), sample.name(), sample.state(),
                    sample.stack().stream().limit(MAX_STACK_DEPTH).toList(), sample.cpuNanos());

            Site site = attribution(thread.latest.stack());
            if (site == null) {
                continue;
            }
            SiteHistory history = sites.get(site);
            if (history == null) {
                if (sites.size() >= MAX_SITES) {
                    droppedSites++;
                    continue;
                }
                history = new SiteHistory();
                sites.put(site, history);
            }
            if (runnable) {
                history.runnable++;
            } else {
                history.waiting++;
            }
        }
    }

    public Snapshot snapshot(long nowNanos) {
        Map<String, Long> durations = new LinkedHashMap<>(phases);
        if (durations.size() < MAX_PHASES || durations.containsKey(phase)) {
            durations.merge(phase, elapsed(nowNanos, phaseStartedNanos), Long::sum);
        }
        List<SiteSummary> siteRows = sites.entrySet().stream()
                .map(entry -> new SiteSummary(entry.getKey().owner, entry.getKey().location,
                        entry.getValue().runnable, entry.getValue().waiting))
                .sorted(Comparator.comparingLong(SiteSummary::runnableSamples).reversed()
                        .thenComparing(Comparator.comparingLong(SiteSummary::waitingSamples).reversed())
                        .thenComparing(SiteSummary::location))
                .toList();
        List<ThreadSummary> threadRows = new ArrayList<>();
        threads.forEach((id, history) -> threadRows.add(new ThreadSummary(id, history.latest.name(),
                history.runnable, history.waiting, history.cpu, history.cpuAvailable, history.latest)));
        threadRows.sort(Comparator.comparingInt((ThreadSummary row) -> importantThread(row.name())).reversed()
                .thenComparing(Comparator.comparingLong(ThreadSummary::cpuNanos).reversed())
                .thenComparing(ThreadSummary::name));
        return new Snapshot(elapsed(nowNanos, startedNanos), phase, progress,
                elapsed(nowNanos, lastProgressNanos), elapsed(nowNanos, lastClientNanos), samples,
                java.util.Collections.unmodifiableMap(durations), siteRows, List.copyOf(threadRows),
                droppedThreads, droppedSites);
    }

    public String report(long nowNanos, String outcome, List<String> mods) {
        Snapshot snapshot = snapshot(nowNanos);
        StringBuilder report = new StringBuilder(8192);
        report.append("=== Wilderness Odyssey Loading Profile ===\n")
                .append("Outcome: ").append(outcome).append('\n')
                .append("Elapsed: ").append(duration(snapshot.elapsedNanos())).append('\n')
                .append("Stage: ").append(snapshot.phase()).append('\n')
                .append("Progress: ").append(snapshot.progress() < 0 ? "unavailable" : snapshot.progress() + "%").append('\n')
                .append("Since last visible stage/percentage change: ").append(duration(snapshot.noProgressNanos())).append('\n')
                .append("Since last client heartbeat: ").append(duration(snapshot.clientSilenceNanos())).append('\n')
                .append("Sampling passes: ").append(snapshot.samples()).append(" (normally once per second)\n\n")
                .append("Interpretation: counts are sampled thread observations, not percentages of total world-loading time.\n")
                .append("Runnable is a JVM state, not proof of CPU use. Waiting/blocked observations are listed separately.\n")
                .append("Thread CPU is the measured increase between observations when supported; it is not assigned to mods.\n")
                .append("The nearest identifiable mod frame is a suspect in a call path, not proof that mod caused the delay.\n")
                .append("Minecraft frames modified by mixins can remain attributed to Minecraft; unresolved frames remain unresolved.\n")
                .append("Remote server generation is not visible in this client JVM.\n")
                .append("Limits: 128 thread histories, 256 sites, 16 phases, 48 frames per sampled thread.\n")
                .append("Excluded history observations: threads=").append(snapshot.droppedThreads())
                .append(", sites=").append(snapshot.droppedSites()).append("\n\n[Observed Stage Durations]\n");
        snapshot.phases().forEach((name, nanos) -> report.append(name).append(": ").append(duration(nanos)).append('\n'));
        report.append("\n[Sampled Code Sites - runnable / waiting-or-blocked]\n");
        snapshot.sites().stream().limit(30).forEach(site -> report.append(site.runnableSamples()).append(" / ")
                .append(site.waitingSamples()).append(" - ").append(site.owner()).append(" - ").append(site.location()).append('\n'));
        report.append("\n[Thread History - runnable / waiting-or-blocked / CPU]\n");
        snapshot.threads().forEach(thread -> report.append(thread.name()).append(" #").append(thread.id()).append(" - ")
                .append(thread.runnableSamples()).append(" / ").append(thread.waitingSamples()).append(" / ")
                .append(thread.cpuAvailable() ? String.format(Locale.ROOT, "%.3f ms", thread.cpuNanos() / 1_000_000.0) : "CPU unavailable")
                .append('\n'));
        report.append("\n[Latest Thread Stacks - up to 32 priority threads]\n");
        snapshot.threads().stream().limit(32).forEach(thread -> {
            report.append('\n').append(thread.name()).append(" #").append(thread.id()).append(" ")
                    .append(thread.latest().state()).append('\n');
            thread.latest().stack().forEach(frame -> report.append("    at ").append(frame).append('\n'));
        });
        report.append("\n[Loaded Mods]\n");
        mods.forEach(mod -> report.append(mod).append('\n'));
        return report.toString();
    }

    public static String duration(long nanos) {
        long seconds = Math.max(0, nanos) / 1_000_000_000;
        return String.format(Locale.ROOT, "%02d:%02d", seconds / 60, seconds % 60);
    }

    private void finishPhase(long nowNanos) {
        if (phases.size() < MAX_PHASES || phases.containsKey(phase)) {
            phases.merge(phase, elapsed(nowNanos, phaseStartedNanos), Long::sum);
        }
    }

    private Site attribution(List<StackTraceElement> stack) {
        Site fallback = null;
        for (StackTraceElement frame : stack) {
            String className = frame.getClassName();
            if (className.startsWith("java.") || className.startsWith("jdk.") || className.startsWith("sun.")) {
                continue;
            }
            String owner = ownerResolver.apply(frame);
            Site site = new Site(owner, className + "." + frame.getMethodName());
            if (fallback == null) {
                fallback = site;
            }
            if (!owner.equals("unresolved") && !owner.equals("minecraft") && !owner.equals("neoforge")) {
                return site;
            }
        }
        return fallback;
    }

    private static long elapsed(long nowNanos, long beforeNanos) {
        return Math.max(0, nowNanos - beforeNanos);
    }

    private static int importantThread(String name) {
        return name.equals("Render thread") || name.equals("Server thread") ? 1 : 0;
    }

    private record Site(String owner, String location) {
    }

    private static final class SiteHistory {
        private long runnable;
        private long waiting;
    }

    private static final class ThreadHistory {
        private long runnable;
        private long waiting;
        private long cpu;
        private long lastCpu = -1;
        private boolean cpuAvailable;
        private ThreadSample latest;
    }

    public record ThreadSample(long id, String name, Thread.State state, List<StackTraceElement> stack, long cpuNanos) {
    }

    public record SiteSummary(String owner, String location, long runnableSamples, long waitingSamples) {
    }

    public record ThreadSummary(long id, String name, long runnableSamples, long waitingSamples, long cpuNanos,
                                boolean cpuAvailable, ThreadSample latest) {
    }

    public record Snapshot(long elapsedNanos, String phase, int progress, long noProgressNanos, long clientSilenceNanos,
                           long samples, Map<String, Long> phases, List<SiteSummary> sites, List<ThreadSummary> threads,
                           long droppedThreads, long droppedSites) {
    }
}
