package com.thunder.wildernessodysseyapi.ecosystem.service;

import com.thunder.wildernessodysseyapi.ecosystem.config.EcosystemConfig;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;

import java.util.Map;
import java.util.WeakHashMap;

/**
 * Per-server gate for block searches, entity scans, and path requests.
 *
 * <p>Each goal independently staggers its due tick, then acquires one slot here
 * before running an expensive evaluation. Denied animals retry later instead
 * of creating an unbounded backlog.</p>
 */
public final class EcosystemUpdateBudget {

    private final Map<MinecraftServer, MutableBudget> servers = new WeakHashMap<>();

    /** Attempts to reserve one expensive evaluation in the current level tick. */
    public boolean tryAcquire(ServerLevel level, long gameTime) {
        MutableBudget budget = servers.computeIfAbsent(level.getServer(), ignored -> new MutableBudget());
        if (budget.tick != gameTime) {
            budget.tick = gameTime;
            budget.used = 0;
            budget.denied = 0;
        }
        if (budget.used >= EcosystemConfig.MAXIMUM_EXPENSIVE_EVALUATIONS_PER_TICK.get()) {
            budget.denied++;
            return false;
        }
        budget.used++;
        return true;
    }

    /** Returns an immutable diagnostics snapshot for one dimension. */
    public Snapshot snapshot(ServerLevel level) {
        MutableBudget budget = servers.get(level.getServer());
        if (budget == null) {
            return new Snapshot(level.getGameTime(), 0, 0,
                    EcosystemConfig.MAXIMUM_EXPENSIVE_EVALUATIONS_PER_TICK.get());
        }
        return new Snapshot(budget.tick, budget.used, budget.denied,
                EcosystemConfig.MAXIMUM_EXPENSIVE_EVALUATIONS_PER_TICK.get());
    }

    /** Releases budget state for an unloading level. */
    public void clear(ServerLevel level) {
        servers.remove(level.getServer());
    }

    /** Current budget usage exposed to the disabled-by-default debug command. */
    public record Snapshot(long tick, int used, int denied, int limit) {
    }

    private static final class MutableBudget {
        private long tick = Long.MIN_VALUE;
        private int used;
        private int denied;
    }
}
