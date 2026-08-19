package com.thunder.wildernessodysseyapi.performance.tickengine;

import java.util.Objects;
import java.util.concurrent.TimeUnit;

/**
 * Reserves safety headroom and converts current pressure into optional WO capacity.
 */
public final class TickBudgetManager {
    private final TickBudget budget = new TickBudget();
    private volatile Settings settings = Settings.defaults();

    /** Applies validated target, headroom, and pressure multipliers. */
    public void configure(Settings settings) {
        this.settings = Objects.requireNonNull(settings, "settings").normalized();
    }

    /** Starts a budget after ordinary server work has reached the post-tick phase. */
    public TickBudget begin(
            long tickStartNanos,
            long optionalStartNanos,
            TickPressure pressure,
            double recoveryMultiplier
    ) {
        Settings current = settings;
        long ordinaryWork = Math.max(0L, optionalStartNanos - tickStartNanos);
        long softBudget = millisecondsToNanos(current.softBudgetMspt());
        long spare = Math.max(0L, softBudget - ordinaryWork);
        double recovery = Double.isFinite(recoveryMultiplier)
                ? Math.max(0.0D, Math.min(1.0D, recoveryMultiplier))
                : 0.0D;
        long allowed = (long) (spare * current.multiplier(pressure) * recovery);
        budget.begin(tickStartNanos, optionalStartNanos, allowed);
        return budget;
    }

    /** Completes accounting for the current optional-work window. */
    public void finish(long endNanos) {
        budget.finish(endNanos);
    }

    public TickBudget current() {
        return budget;
    }

    private static long millisecondsToNanos(double milliseconds) {
        return (long) (milliseconds * TimeUnit.MILLISECONDS.toNanos(1L));
    }

    /** Tick target and load-specific budget fractions. */
    public record Settings(
            double targetMspt,
            double softBudgetMspt,
            double relaxedMultiplier,
            double busyMultiplier,
            double highMultiplier,
            double criticalMultiplier,
            double overloadedMultiplier
    ) {
        public static Settings defaults() {
            return new Settings(50.0D, 45.0D, 1.0D, 0.70D, 0.35D, 0.10D, 0.0D);
        }

        private Settings normalized() {
            if (!Double.isFinite(targetMspt) || targetMspt <= 0.0D
                    || !Double.isFinite(softBudgetMspt) || softBudgetMspt < 0.0D) {
                return defaults();
            }
            return new Settings(
                    targetMspt,
                    Math.min(targetMspt, softBudgetMspt),
                    clamp(relaxedMultiplier),
                    clamp(busyMultiplier),
                    clamp(highMultiplier),
                    clamp(criticalMultiplier),
                    clamp(overloadedMultiplier)
            );
        }

        public double multiplier(TickPressure pressure) {
            return switch (Objects.requireNonNull(pressure, "pressure")) {
                case RELAXED -> relaxedMultiplier;
                case BUSY -> busyMultiplier;
                case HIGH -> highMultiplier;
                case CRITICAL -> criticalMultiplier;
                case OVERLOADED -> overloadedMultiplier;
            };
        }

        private static double clamp(double value) {
            return Double.isFinite(value) ? Math.max(0.0D, Math.min(1.0D, value)) : 0.0D;
        }
    }
}
