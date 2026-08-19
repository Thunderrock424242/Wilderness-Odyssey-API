package com.thunder.wildernessodysseyapi.performance.tickengine;

import java.util.Arrays;
import java.util.Objects;

/**
 * Allocation-free rolling MSPT monitor with confirmed escalation and hysteretic recovery.
 *
 * <p>One isolated slow tick changes the rolling samples but cannot immediately
 * force a pressure transition. Recovery moves only one level after sustained
 * samples below the configured margin.</p>
 */
public final class TickMonitor {
    private final double[] shortSamples;
    private final double[] mediumSamples;

    private Thresholds thresholds;
    private int shortIndex;
    private volatile int shortCount;
    private volatile double shortTotal;
    private int mediumIndex;
    private volatile int mediumCount;
    private volatile double mediumTotal;
    private volatile double recentMaximum;
    private volatile double currentMspt;
    private volatile long tickCount;
    private volatile long overloadedTickCount;
    private volatile int consecutiveOverloadedTicks;
    private volatile TickPressure pressure = TickPressure.RELAXED;
    private TickPressure pendingEscalation = TickPressure.RELAXED;
    private int escalationSamples;
    private int recoverySamples;

    public TickMonitor(Thresholds thresholds) {
        this(thresholds, 20, 100);
    }

    public TickMonitor(Thresholds thresholds, int shortWindow, int mediumWindow) {
        this.thresholds = Objects.requireNonNull(thresholds, "thresholds").validated();
        this.shortSamples = new double[Math.max(1, shortWindow)];
        this.mediumSamples = new double[Math.max(this.shortSamples.length, mediumWindow)];
    }

    /** Applies new thresholds without discarding rolling timing evidence. */
    public void configure(Thresholds thresholds) {
        this.thresholds = Objects.requireNonNull(thresholds, "thresholds").validated();
    }

    /** Records one complete measured server tick in milliseconds. */
    public TickPressure recordTick(double measuredMspt) {
        currentMspt = Double.isFinite(measuredMspt) ? Math.max(0.0D, measuredMspt) : 0.0D;
        tickCount++;
        if (currentMspt > thresholds.overloadedMspt()) {
            overloadedTickCount++;
            consecutiveOverloadedTicks++;
        } else {
            consecutiveOverloadedTicks = 0;
        }

        shortTotal = replace(shortSamples, shortIndex, shortCount, shortTotal, currentMspt);
        shortIndex = (shortIndex + 1) % shortSamples.length;
        shortCount = Math.min(shortSamples.length, shortCount + 1);

        double replacedMedium = mediumSamples[mediumIndex];
        mediumTotal = replace(mediumSamples, mediumIndex, mediumCount, mediumTotal, currentMspt);
        mediumIndex = (mediumIndex + 1) % mediumSamples.length;
        mediumCount = Math.min(mediumSamples.length, mediumCount + 1);
        if (currentMspt >= recentMaximum) {
            recentMaximum = currentMspt;
        } else if (mediumCount == mediumSamples.length && replacedMedium >= recentMaximum) {
            recentMaximum = maximumSample(mediumSamples);
        }

        updatePressure(pressureFor(shortAverageMspt()));
        return pressure;
    }

    /** Resets process-scoped rolling state when a different server starts. */
    public void reset() {
        Arrays.fill(shortSamples, 0.0D);
        Arrays.fill(mediumSamples, 0.0D);
        shortIndex = 0;
        shortCount = 0;
        shortTotal = 0.0D;
        mediumIndex = 0;
        mediumCount = 0;
        mediumTotal = 0.0D;
        recentMaximum = 0.0D;
        currentMspt = 0.0D;
        tickCount = 0L;
        overloadedTickCount = 0L;
        consecutiveOverloadedTicks = 0;
        pressure = TickPressure.RELAXED;
        pendingEscalation = TickPressure.RELAXED;
        escalationSamples = 0;
        recoverySamples = 0;
    }

    public double currentMspt() {
        return currentMspt;
    }

    public double shortAverageMspt() {
        return shortCount == 0 ? 0.0D : shortTotal / shortCount;
    }

    public double mediumAverageMspt() {
        return mediumCount == 0 ? 0.0D : mediumTotal / mediumCount;
    }

    public double recentMaximumMspt() {
        return recentMaximum;
    }

    public long tickCount() {
        return tickCount;
    }

    public long overloadedTickCount() {
        return overloadedTickCount;
    }

    public int consecutiveOverloadedTicks() {
        return consecutiveOverloadedTicks;
    }

    public TickPressure pressure() {
        return pressure;
    }

    public double estimatedTps() {
        double average = mediumAverageMspt();
        return average <= 0.0D ? 20.0D : Math.min(20.0D, 1000.0D / average);
    }

    private void updatePressure(TickPressure observed) {
        if (observed.ordinal() > pressure.ordinal()) {
            recoverySamples = 0;
            if (observed != pendingEscalation) {
                pendingEscalation = observed;
                escalationSamples = 1;
            } else {
                escalationSamples++;
            }
            if (escalationSamples >= thresholds.escalationSamples()) {
                pressure = observed;
                pendingEscalation = pressure;
                escalationSamples = 0;
            }
            return;
        }

        pendingEscalation = pressure;
        escalationSamples = 0;
        if (observed.ordinal() >= pressure.ordinal() || pressure == TickPressure.RELAXED) {
            recoverySamples = 0;
            return;
        }

        double recoveryBoundary = entryBoundary(pressure) - thresholds.recoveryMarginMspt();
        if (shortAverageMspt() < recoveryBoundary) {
            recoverySamples++;
            if (recoverySamples >= thresholds.recoverySamples()) {
                pressure = pressure.recoverOneLevel();
                recoverySamples = 0;
            }
        } else {
            recoverySamples = 0;
        }
    }

    private TickPressure pressureFor(double mspt) {
        if (mspt >= thresholds.overloadedMspt()) {
            return TickPressure.OVERLOADED;
        }
        if (mspt >= thresholds.criticalMspt()) {
            return TickPressure.CRITICAL;
        }
        if (mspt >= thresholds.highMspt()) {
            return TickPressure.HIGH;
        }
        if (mspt >= thresholds.busyMspt()) {
            return TickPressure.BUSY;
        }
        return TickPressure.RELAXED;
    }

    private double entryBoundary(TickPressure value) {
        return switch (value) {
            case RELAXED -> 0.0D;
            case BUSY -> thresholds.busyMspt();
            case HIGH -> thresholds.highMspt();
            case CRITICAL -> thresholds.criticalMspt();
            case OVERLOADED -> thresholds.overloadedMspt();
        };
    }

    private static double replace(double[] samples, int index, int count, double total, double sample) {
        double previous = count < samples.length ? 0.0D : samples[index];
        samples[index] = sample;
        return total - previous + sample;
    }

    private static double maximumSample(double[] samples) {
        double maximum = 0.0D;
        for (double sample : samples) {
            maximum = Math.max(maximum, sample);
        }
        return maximum;
    }

    /** Pressure thresholds and transition confirmation policy. */
    public record Thresholds(
            double busyMspt,
            double highMspt,
            double criticalMspt,
            double overloadedMspt,
            double recoveryMarginMspt,
            int escalationSamples,
            int recoverySamples
    ) {
        public static Thresholds defaults() {
            return new Thresholds(30.0D, 40.0D, 47.0D, 50.0D, 2.0D, 3, 40);
        }

        private Thresholds validated() {
            if (!Double.isFinite(busyMspt) || !Double.isFinite(highMspt)
                    || !Double.isFinite(criticalMspt) || !Double.isFinite(overloadedMspt)
                    || busyMspt < 0.0D || highMspt <= busyMspt
                    || criticalMspt <= highMspt || overloadedMspt <= criticalMspt) {
                return defaults();
            }
            return new Thresholds(
                    busyMspt,
                    highMspt,
                    criticalMspt,
                    overloadedMspt,
                    Double.isFinite(recoveryMarginMspt) ? Math.max(0.0D, recoveryMarginMspt) : 2.0D,
                    Math.max(1, escalationSamples),
                    Math.max(1, recoverySamples)
            );
        }
    }
}
