package com.thunder.wildernessodysseyapi.performance.background;

/**
 * Narrow control surface through which an external load manager can constrain
 * Wilderness Odyssey background work without depending on scheduler internals.
 */
public record BackgroundBudgetControl(
        double budgetMultiplier,
        long maximumBudgetNanos,
        boolean backgroundAllowed,
        boolean idleAllowed
) {
    public static final BackgroundBudgetControl UNRESTRICTED =
            new BackgroundBudgetControl(1.0D, Long.MAX_VALUE, true, true);

    public BackgroundBudgetControl {
        if (!Double.isFinite(budgetMultiplier)) {
            budgetMultiplier = 1.0D;
        }
        budgetMultiplier = Math.max(0.0D, Math.min(1.0D, budgetMultiplier));
        maximumBudgetNanos = Math.max(0L, maximumBudgetNanos);
    }
}
