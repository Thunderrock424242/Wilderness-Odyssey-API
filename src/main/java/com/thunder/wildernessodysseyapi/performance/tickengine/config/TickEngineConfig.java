package com.thunder.wildernessodysseyapi.performance.tickengine.config;

import com.thunder.wildernessodysseyapi.performance.tickengine.TickBudgetManager;
import com.thunder.wildernessodysseyapi.performance.tickengine.TickMonitor;
import com.thunder.wildernessodysseyapi.performance.tickengine.TickWorkScheduler;
import net.neoforged.neoforge.common.ModConfigSpec;

/** Server configuration for the additive Wilderness Odyssey Tick Engine. */
public final class TickEngineConfig {
    public static final ModConfigSpec CONFIG_SPEC;

    public static final ModConfigSpec.BooleanValue ENABLED;
    public static final ModConfigSpec.DoubleValue TARGET_MSPT;
    public static final ModConfigSpec.DoubleValue SOFT_BUDGET_MSPT;
    public static final ModConfigSpec.DoubleValue BUSY_MSPT;
    public static final ModConfigSpec.DoubleValue HIGH_MSPT;
    public static final ModConfigSpec.DoubleValue CRITICAL_MSPT;
    public static final ModConfigSpec.DoubleValue OVERLOADED_MSPT;
    public static final ModConfigSpec.DoubleValue RECOVERY_MARGIN_MSPT;
    public static final ModConfigSpec.IntValue ESCALATION_SAMPLES;
    public static final ModConfigSpec.IntValue RECOVERY_TICKS;
    public static final ModConfigSpec.DoubleValue RELAXED_BUDGET_MULTIPLIER;
    public static final ModConfigSpec.DoubleValue BUSY_BUDGET_MULTIPLIER;
    public static final ModConfigSpec.DoubleValue HIGH_BUDGET_MULTIPLIER;
    public static final ModConfigSpec.DoubleValue CRITICAL_BUDGET_MULTIPLIER;
    public static final ModConfigSpec.DoubleValue OVERLOADED_BUDGET_MULTIPLIER;
    public static final ModConfigSpec.IntValue MAX_TASKS_PER_TICK;
    public static final ModConfigSpec.IntValue MAX_DEFERRED_TASKS;
    public static final ModConfigSpec.IntValue MAX_TASKS_PER_SUBSYSTEM;
    public static final ModConfigSpec.BooleanValue PROFILING;
    public static final ModConfigSpec.DoubleValue SLOW_SUBSYSTEM_WARNING_MS;
    public static final ModConfigSpec.IntValue SLOW_WARNING_INTERVAL_TICKS;
    public static final ModConfigSpec.BooleanValue ADAPTIVE_THROTTLE;
    public static final ModConfigSpec.BooleanValue TICK_DEBT_COLLAPSING;
    public static final ModConfigSpec.IntValue MAX_INDIVIDUAL_DEBT_STEPS;

    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

    static {
        BUILDER.push("tickEngine");
        ENABLED = BUILDER.comment("Controls only opt-in Wilderness Odyssey work; vanilla ticking is never replaced.")
                .define("enabled", true);
        TARGET_MSPT = BUILDER.comment("Minecraft server target duration used for diagnostics.")
                .defineInRange("targetMspt", 50.0D, 10.0D, 200.0D);
        SOFT_BUDGET_MSPT = BUILDER.comment("Safety target below which optional WO work may consume remaining time.")
                .defineInRange("softBudgetMs", 45.0D, 0.0D, 200.0D);

        BUILDER.push("pressure");
        BUSY_MSPT = BUILDER.comment("Rolling short-average threshold for BUSY pressure.")
                .defineInRange("busy", 30.0D, 1.0D, 200.0D);
        HIGH_MSPT = BUILDER.comment("Rolling short-average threshold for HIGH pressure.")
                .defineInRange("high", 40.0D, 1.0D, 200.0D);
        CRITICAL_MSPT = BUILDER.comment("Rolling short-average threshold for CRITICAL pressure.")
                .defineInRange("critical", 47.0D, 1.0D, 200.0D);
        OVERLOADED_MSPT = BUILDER.comment("Rolling short-average threshold for OVERLOADED pressure.")
                .defineInRange("overloaded", 50.0D, 1.0D, 500.0D);
        RECOVERY_MARGIN_MSPT = BUILDER.comment("Required MSPT margin below the entry threshold before recovery progresses.")
                .defineInRange("recoveryMargin", 2.0D, 0.0D, 25.0D);
        ESCALATION_SAMPLES = BUILDER.comment("Consecutive rolling samples required before pressure escalates.")
                .defineInRange("escalationSamples", 3, 1, 100);
        RECOVERY_TICKS = BUILDER.comment("Sustained healthy samples required for each one-level recovery step.")
                .defineInRange("recoveryTicks", 40, 1, 12000);
        BUILDER.pop();

        BUILDER.push("budgetMultipliers");
        RELAXED_BUDGET_MULTIPLIER = defineMultiplier("relaxed", 1.0D);
        BUSY_BUDGET_MULTIPLIER = defineMultiplier("busy", 0.70D);
        HIGH_BUDGET_MULTIPLIER = defineMultiplier("high", 0.35D);
        CRITICAL_BUDGET_MULTIPLIER = defineMultiplier("critical", 0.10D);
        OVERLOADED_BUDGET_MULTIPLIER = defineMultiplier("overloaded", 0.0D);
        BUILDER.pop();

        BUILDER.push("deferredWork");
        MAX_TASKS_PER_TICK = BUILDER.comment("Maximum tick-aware task steps processed in one tick.")
                .defineInRange("maxTasksPerTick", 64, 1, 4096);
        MAX_DEFERRED_TASKS = BUILDER.comment("Global bounded Tick Engine queue capacity.")
                .defineInRange("maxDeferredTasks", 2048, 16, 65536);
        MAX_TASKS_PER_SUBSYSTEM = BUILDER.comment("Per-subsystem queue cap.")
                .defineInRange("maxTasksPerSubsystem", 256, 1, 8192);
        BUILDER.pop();

        BUILDER.push("profiling");
        PROFILING = BUILDER.comment("Collects lightweight timing only for explicitly wrapped WO work.")
                .define("enabled", true);
        SLOW_SUBSYSTEM_WARNING_MS = BUILDER.comment("Average WO subsystem time that triggers a rate-limited warning.")
                .defineInRange("slowSubsystemWarningMs", 5.0D, 0.0D, 100.0D);
        SLOW_WARNING_INTERVAL_TICKS = BUILDER.comment("Minimum game ticks between warnings for one subsystem.")
                .defineInRange("warningIntervalTicks", 1200, 20, 72000);
        BUILDER.pop();

        ADAPTIVE_THROTTLE = BUILDER.comment("Allows registered WO systems to query adaptive intervals.")
                .define("adaptiveThrottle", true);
        TICK_DEBT_COLLAPSING = BUILDER.comment("Enables explicit collapsed/discarded/individual missed-tick policies.")
                .define("tickDebtCollapsing", true);
        MAX_INDIVIDUAL_DEBT_STEPS = BUILDER.comment("Maximum individually required catch-up steps performed per call.")
                .defineInRange("maxIndividualDebtSteps", 8, 1, 1000);

        BUILDER.pop();
        CONFIG_SPEC = BUILDER.build();
    }

    private TickEngineConfig() {
    }

    /** Returns sanitized values or safe defaults when config state is unavailable. */
    public static Values values() {
        try {
            return sanitize(new Values(
                    ENABLED.get(), TARGET_MSPT.get(), SOFT_BUDGET_MSPT.get(),
                    BUSY_MSPT.get(), HIGH_MSPT.get(), CRITICAL_MSPT.get(), OVERLOADED_MSPT.get(),
                    RECOVERY_MARGIN_MSPT.get(), ESCALATION_SAMPLES.get(), RECOVERY_TICKS.get(),
                    RELAXED_BUDGET_MULTIPLIER.get(), BUSY_BUDGET_MULTIPLIER.get(),
                    HIGH_BUDGET_MULTIPLIER.get(), CRITICAL_BUDGET_MULTIPLIER.get(),
                    OVERLOADED_BUDGET_MULTIPLIER.get(), MAX_TASKS_PER_TICK.get(),
                    MAX_DEFERRED_TASKS.get(), MAX_TASKS_PER_SUBSYSTEM.get(), PROFILING.get(),
                    SLOW_SUBSYSTEM_WARNING_MS.get(), SLOW_WARNING_INTERVAL_TICKS.get(),
                    ADAPTIVE_THROTTLE.get(), TICK_DEBT_COLLAPSING.get(), MAX_INDIVIDUAL_DEBT_STEPS.get()
            ));
        } catch (RuntimeException exception) {
            return defaults();
        }
    }

    /** Returns built-in defaults without requiring a loaded server config. */
    public static Values defaults() {
        return new Values(
                true, 50.0D, 45.0D, 30.0D, 40.0D, 47.0D, 50.0D,
                2.0D, 3, 40, 1.0D, 0.70D, 0.35D, 0.10D, 0.0D,
                64, 2048, 256, true, 5.0D, 1200, true, true, 8
        );
    }

    /** Normalizes externally supplied values and falls back if thresholds are inconsistent. */
    public static Values sanitize(Values values) {
        if (values == null
                || !finitePositive(values.targetMspt())
                || !Double.isFinite(values.softBudgetMspt())
                || !finitePositive(values.busyMspt())
                || !finitePositive(values.highMspt())
                || !finitePositive(values.criticalMspt())
                || !finitePositive(values.overloadedMspt())
                || values.highMspt() <= values.busyMspt()
                || values.criticalMspt() <= values.highMspt()
                || values.overloadedMspt() <= values.criticalMspt()
                || !Double.isFinite(values.recoveryMarginMspt())
                || values.recoveryMarginMspt() < 0.0D
                || values.recoveryMarginMspt() >= values.busyMspt()) {
            return defaults();
        }
        int maximumQueued = Math.max(1, values.maximumDeferredTasks());
        return new Values(
                values.enabled(),
                values.targetMspt(),
                Math.max(0.0D, Math.min(values.targetMspt(), values.softBudgetMspt())),
                values.busyMspt(),
                values.highMspt(),
                values.criticalMspt(),
                values.overloadedMspt(),
                values.recoveryMarginMspt(),
                Math.max(1, values.escalationSamples()),
                Math.max(1, values.recoveryTicks()),
                clampMultiplier(values.relaxedBudgetMultiplier()),
                clampMultiplier(values.busyBudgetMultiplier()),
                clampMultiplier(values.highBudgetMultiplier()),
                clampMultiplier(values.criticalBudgetMultiplier()),
                clampMultiplier(values.overloadedBudgetMultiplier()),
                Math.max(1, values.maximumTasksPerTick()),
                maximumQueued,
                Math.max(1, Math.min(maximumQueued, values.maximumTasksPerSubsystem())),
                values.profiling(),
                Double.isFinite(values.slowSubsystemWarningMillis())
                        ? Math.max(0.0D, values.slowSubsystemWarningMillis()) : 5.0D,
                Math.max(1, values.slowWarningIntervalTicks()),
                values.adaptiveThrottle(),
                values.tickDebtCollapsing(),
                Math.max(1, values.maximumIndividualDebtSteps())
        );
    }

    private static ModConfigSpec.DoubleValue defineMultiplier(String name, double defaultValue) {
        return BUILDER.comment("Fraction of remaining soft-budget time allowed at " + name + " pressure.")
                .defineInRange(name, defaultValue, 0.0D, 1.0D);
    }

    private static boolean finitePositive(double value) {
        return Double.isFinite(value) && value > 0.0D;
    }

    private static double clampMultiplier(double value) {
        return Double.isFinite(value) ? Math.max(0.0D, Math.min(1.0D, value)) : 0.0D;
    }

    /** Immutable engine config shared by runtime components. */
    public record Values(
            boolean enabled,
            double targetMspt,
            double softBudgetMspt,
            double busyMspt,
            double highMspt,
            double criticalMspt,
            double overloadedMspt,
            double recoveryMarginMspt,
            int escalationSamples,
            int recoveryTicks,
            double relaxedBudgetMultiplier,
            double busyBudgetMultiplier,
            double highBudgetMultiplier,
            double criticalBudgetMultiplier,
            double overloadedBudgetMultiplier,
            int maximumTasksPerTick,
            int maximumDeferredTasks,
            int maximumTasksPerSubsystem,
            boolean profiling,
            double slowSubsystemWarningMillis,
            int slowWarningIntervalTicks,
            boolean adaptiveThrottle,
            boolean tickDebtCollapsing,
            int maximumIndividualDebtSteps
    ) {
        public TickMonitor.Thresholds monitorThresholds() {
            return new TickMonitor.Thresholds(
                    busyMspt, highMspt, criticalMspt, overloadedMspt,
                    recoveryMarginMspt, escalationSamples, recoveryTicks
            );
        }

        public TickBudgetManager.Settings budgetSettings() {
            return new TickBudgetManager.Settings(
                    targetMspt, softBudgetMspt, relaxedBudgetMultiplier, busyBudgetMultiplier,
                    highBudgetMultiplier, criticalBudgetMultiplier, overloadedBudgetMultiplier
            );
        }

        public TickWorkScheduler.Settings schedulerSettings() {
            return new TickWorkScheduler.Settings(
                    enabled, maximumTasksPerTick, maximumDeferredTasks, maximumTasksPerSubsystem
            );
        }
    }
}
