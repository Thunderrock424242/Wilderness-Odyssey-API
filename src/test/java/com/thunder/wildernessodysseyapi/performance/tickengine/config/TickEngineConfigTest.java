package com.thunder.wildernessodysseyapi.performance.tickengine.config;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Verifies invalid threshold fallback and disabled scheduler configuration. */
class TickEngineConfigTest {

    @Test
    void inconsistentThresholdsFallBackToSafeDefaults() {
        TickEngineConfig.Values invalid = new TickEngineConfig.Values(
                true, 50.0D, 45.0D, 40.0D, 30.0D, 47.0D, 50.0D,
                2.0D, 3, 40, 1.0D, 0.7D, 0.35D, 0.1D, 0.0D,
                64, 2048, 256, true, 5.0D, 1200, true, true, 8
        );

        TickEngineConfig.Values values = TickEngineConfig.sanitize(invalid);

        assertEquals(30.0D, values.busyMspt());
        assertEquals(40.0D, values.highMspt());
        assertTrue(values.softBudgetMspt() < values.targetMspt());
    }

    @Test
    void disabledEngineProducesDisabledTickSchedulerSettings() {
        TickEngineConfig.Values defaults = TickEngineConfig.defaults();
        TickEngineConfig.Values disabled = new TickEngineConfig.Values(
                false, defaults.targetMspt(), defaults.softBudgetMspt(), defaults.busyMspt(),
                defaults.highMspt(), defaults.criticalMspt(), defaults.overloadedMspt(),
                defaults.recoveryMarginMspt(), defaults.escalationSamples(), defaults.recoveryTicks(),
                defaults.relaxedBudgetMultiplier(), defaults.busyBudgetMultiplier(), defaults.highBudgetMultiplier(),
                defaults.criticalBudgetMultiplier(), defaults.overloadedBudgetMultiplier(),
                defaults.maximumTasksPerTick(), defaults.maximumDeferredTasks(), defaults.maximumTasksPerSubsystem(),
                defaults.profiling(), defaults.slowSubsystemWarningMillis(), defaults.slowWarningIntervalTicks(),
                defaults.adaptiveThrottle(), defaults.tickDebtCollapsing(), defaults.maximumIndividualDebtSteps()
        );

        assertEquals(false, TickEngineConfig.sanitize(disabled).schedulerSettings().enabled());
    }

    @Test
    void nonFiniteThresholdAndImpossibleRecoveryMarginUseDefaults() {
        TickEngineConfig.Values defaults = TickEngineConfig.defaults();
        TickEngineConfig.Values nonFinite = copyThresholds(defaults, Double.NaN, defaults.recoveryMarginMspt());
        TickEngineConfig.Values impossibleMargin = copyThresholds(
                defaults,
                defaults.highMspt(),
                defaults.busyMspt()
        );

        assertEquals(defaults, TickEngineConfig.sanitize(nonFinite));
        assertEquals(defaults, TickEngineConfig.sanitize(impossibleMargin));
    }

    private static TickEngineConfig.Values copyThresholds(
            TickEngineConfig.Values values,
            double highMspt,
            double recoveryMarginMspt
    ) {
        return new TickEngineConfig.Values(
                values.enabled(), values.targetMspt(), values.softBudgetMspt(), values.busyMspt(),
                highMspt, values.criticalMspt(), values.overloadedMspt(), recoveryMarginMspt,
                values.escalationSamples(), values.recoveryTicks(), values.relaxedBudgetMultiplier(),
                values.busyBudgetMultiplier(), values.highBudgetMultiplier(), values.criticalBudgetMultiplier(),
                values.overloadedBudgetMultiplier(), values.maximumTasksPerTick(), values.maximumDeferredTasks(),
                values.maximumTasksPerSubsystem(), values.profiling(), values.slowSubsystemWarningMillis(),
                values.slowWarningIntervalTicks(), values.adaptiveThrottle(), values.tickDebtCollapsing(),
                values.maximumIndividualDebtSteps()
        );
    }
}
