package com.thunder.wildernessodysseyapi.performance.tickengine;

import com.thunder.wildernessodysseyapi.performance.background.ActivityLevel;

import java.util.Collection;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Converts TickPressure and optional region activity into opt-in update intervals.
 *
 * <p>This class never skips Minecraft entity or block-entity base ticks. Owners
 * call it only around their own expensive custom calculations.</p>
 */
public final class AdaptiveThrottle {
    private final ConcurrentHashMap<String, SubsystemPolicy> policies = new ConcurrentHashMap<>();
    private volatile SubsystemPolicy[] policySnapshot = new SubsystemPolicy[0];
    private volatile boolean enabled = true;

    /** Enables or bypasses adaptive interval expansion. */
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    /** Registers a subsystem once so accidental policy replacement cannot change behavior live. */
    public synchronized void register(SubsystemPolicy policy) {
        Objects.requireNonNull(policy, "policy");
        SubsystemPolicy previous = policies.putIfAbsent(policy.id(), policy);
        if (previous != null && !previous.equals(policy)) {
            throw new IllegalStateException("Tick Engine subsystem already registered: " + policy.id());
        }
        if (previous == null) {
            policySnapshot = policies.values().toArray(SubsystemPolicy[]::new);
        }
    }

    public SubsystemPolicy policy(String subsystemId) {
        return policies.get(normalize(subsystemId));
    }

    /** Returns a snapshot only for configuration/debug paths, never for hot tick decisions. */
    public Collection<SubsystemPolicy> registeredPolicies() {
        return java.util.List.copyOf(policies.values());
    }

    /** Returns the effective interval for expensive custom work. */
    public int intervalFor(
            String subsystemId,
            int normalIntervalTicks,
            TickPressure pressure,
            ActivityLevel activityLevel,
            double recoveryMultiplier
    ) {
        int normal = Math.max(1, normalIntervalTicks);
        if (!enabled) {
            return normal;
        }
        SubsystemPolicy policy = policies.get(normalize(subsystemId));
        if (policy == null) {
            return normal;
        }
        int baseInterval = policy.suspensionAllowed()
                ? normal
                : Math.min(normal, policy.maximumIntervalTicks());

        double multiplier = pressureMultiplier(policy.importance(), pressure)
                * activityMultiplier(activityLevel)
                * clamp(recoveryMultiplier);
        if (multiplier <= 0.0D) {
            return policy.suspensionAllowed() ? Integer.MAX_VALUE : policy.maximumIntervalTicks();
        }
        long expanded = (long) Math.ceil(baseInterval / multiplier);
        if (!policy.suspensionAllowed()) {
            expanded = Math.min(expanded, policy.maximumIntervalTicks());
        }
        return (int) Math.max(baseInterval, Math.min(Integer.MAX_VALUE, expanded));
    }

    /** Cheap modulo-free due check that tolerates tick-counter rollback. */
    public boolean shouldRun(long currentTick, long lastRunTick, int intervalTicks) {
        if (intervalTicks == Integer.MAX_VALUE) {
            return false;
        }
        return currentTick < lastRunTick || currentTick - lastRunTick >= Math.max(1, intervalTicks);
    }

    /** Counts registered systems currently below full-rate operation for diagnostics. */
    public int throttledSubsystemCount(TickPressure pressure, double recoveryMultiplier) {
        if (!enabled) {
            return 0;
        }
        int throttled = 0;
        for (SubsystemPolicy policy : policySnapshot) {
            if (pressureMultiplier(policy.importance(), pressure) * clamp(recoveryMultiplier) < 0.999D) {
                throttled++;
            }
        }
        return throttled;
    }

    private static double pressureMultiplier(TickPriority importance, TickPressure pressure) {
        return switch (importance) {
            case CRITICAL -> 1.0D;
            case GAMEPLAY -> switch (pressure) {
                case RELAXED, BUSY -> 1.0D;
                case HIGH -> 0.9D;
                case CRITICAL -> 0.75D;
                case OVERLOADED -> 0.5D;
            };
            case NORMAL -> switch (pressure) {
                case RELAXED -> 1.0D;
                case BUSY -> 0.85D;
                case HIGH -> 0.55D;
                case CRITICAL -> 0.25D;
                case OVERLOADED -> 0.0D;
            };
            case BACKGROUND -> switch (pressure) {
                case RELAXED -> 1.0D;
                case BUSY -> 0.6D;
                case HIGH -> 0.25D;
                case CRITICAL, OVERLOADED -> 0.0D;
            };
            case IDLE -> switch (pressure) {
                case RELAXED -> 1.0D;
                case BUSY -> 0.25D;
                case HIGH, CRITICAL, OVERLOADED -> 0.0D;
            };
        };
    }

    private static double activityMultiplier(ActivityLevel activityLevel) {
        return switch (Objects.requireNonNullElse(activityLevel, ActivityLevel.ACTIVE)) {
            case ACTIVE -> 1.0D;
            case NEARBY -> 0.75D;
            case BACKGROUND -> 0.3D;
            case DORMANT -> 0.0D;
        };
    }

    private static double clamp(double value) {
        return Double.isFinite(value) ? Math.max(0.0D, Math.min(1.0D, value)) : 0.0D;
    }

    private static String normalize(String value) {
        return Objects.requireNonNullElse(value, "unknown").trim().toLowerCase(java.util.Locale.ROOT);
    }
}
