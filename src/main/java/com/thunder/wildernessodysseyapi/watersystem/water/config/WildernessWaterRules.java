package com.thunder.wildernessodysseyapi.watersystem.water.config;

import com.thunder.wildernessodysseyapi.core.ModConstants;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.Level;

import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * Registers and evaluates world-level Wilderness water controls.
 *
 * <p>The server config and gamerule choose the mode only when a world first
 * creates its persisted authority record. Bare gamerule changes are restored;
 * the only supported transition is the operator's bounded, verified legacy-
 * water conversion before enabling authority. This prevents a temporary toggle
 * from letting the namespaced fluid run native flow while canonical storage is
 * paused.</p>
 */
public final class WildernessWaterRules {

    private static final Map<MinecraftServer, WaterAuthorityModeSavedData> SERVER_MODES =
            Collections.synchronizedMap(new WeakHashMap<>());
    private static final ThreadLocal<Boolean> RESTORING_RULE = ThreadLocal.withInitial(() -> false);

    /** Enables Wilderness Odyssey water authority, migration, rendering, and local simulation for a world. */
    public static final GameRules.Key<GameRules.BooleanValue> ENABLE_WILDERNESS_ODYSSEY_WATER =
            GameRules.register(
                    "enableWildernessOdysseyWater",
                    GameRules.Category.UPDATES,
                    GameRules.BooleanValue.create(true, WildernessWaterRules::onGameRuleChanged)
            );

    private WildernessWaterRules() {
    }

    /**
     * Forces class loading so the custom gamerule is registered before worlds
     * are created or loaded.
     */
    public static void bootstrap() {
        // Intentionally empty.
    }

    /** Returns whether Wilderness water is enabled globally and for this world. */
    public static boolean isEnabled(Level level) {
        if (level == null) {
            return WaterSimulationConfig.wildernessWaterEnabled();
        }
        if (level instanceof ServerLevel serverLevel) {
            WaterAuthorityModeSavedData mode = mode(serverLevel.getServer());
            restoreGameRule(serverLevel.getServer(), mode.enabled(), false);
            return mode.enabled();
        }

        // The server restores and synchronizes this gamerule to the persisted
        // value, so clients never depend on an unsynchronized local config.
        return level.getGameRules().getBoolean(ENABLE_WILDERNESS_ODYSSEY_WATER);
    }

    /** Returns diagnostics explaining the persisted and currently requested mode. */
    public static ModeStatus status(ServerLevel level) {
        WaterAuthorityModeSavedData mode = mode(level.getServer());
        return new ModeStatus(
                mode.enabled(),
                level.getGameRules().getBoolean(ENABLE_WILDERNESS_ODYSSEY_WATER),
                WaterSimulationConfig.wildernessWaterEnabled()
        );
    }

    /**
     * Enables persisted authority after the caller completes and verifies an
     * explicit bounded conversion. Disabling is intentionally not exposed:
     * proving a rollback safe requires reconciling water in unloaded chunks.
     *
     * @return {@code true} when the persisted setting changed
     */
    public static boolean enableAfterExplicitConversion(ServerLevel level) {
        MinecraftServer server = level.getServer();
        WaterAuthorityModeSavedData mode = mode(server);
        if (mode.enabled()) {
            restoreGameRule(server, true, false);
            return false;
        }
        mode.transitionTo(true);
        restoreGameRule(server, true, true);
        ModConstants.LOGGER.info("Enabled persisted Wilderness water authority after explicit conversion");
        return true;
    }

    private static WaterAuthorityModeSavedData mode(MinecraftServer server) {
        return SERVER_MODES.computeIfAbsent(server, ignored -> {
            boolean startupEnabled = WaterSimulationConfig.wildernessWaterEnabled()
                    && server.getGameRules().getBoolean(ENABLE_WILDERNESS_ODYSSEY_WATER);
            return WaterAuthorityModeSavedData.get(server, startupEnabled);
        });
    }

    private static void onGameRuleChanged(MinecraftServer server, GameRules.BooleanValue value) {
        if (server == null || RESTORING_RULE.get()) {
            return;
        }
        boolean active = mode(server).enabled();
        if (value.get() == active) {
            return;
        }

        ModConstants.LOGGER.warn(
                "Ignored live Wilderness water authority toggle to {}; persisted mode remains {}. "
                        + "Use /wowater mode set on <radius> for a bounded verified activation; "
                        + "automatic rollback is refused because unloaded chunks cannot be reconciled safely.",
                value.get(),
                active
        );
        restoreGameRule(server, active, true);
    }

    private static void restoreGameRule(MinecraftServer server, boolean enabled, boolean force) {
        GameRules.BooleanValue rule = server.getGameRules().getRule(ENABLE_WILDERNESS_ODYSSEY_WATER);
        if ((!force && rule.get() == enabled) || RESTORING_RULE.get()) {
            return;
        }
        RESTORING_RULE.set(true);
        try {
            rule.set(enabled, server);
        } finally {
            RESTORING_RULE.remove();
        }
    }

    /** Immutable authority-mode diagnostics exposed by {@code /wowater mode}. */
    public record ModeStatus(boolean active, boolean gameRule, boolean startupConfig) {
    }
}
