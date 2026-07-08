package com.thunder.wildernessodysseyapi.watersystem.water.config;

import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.Level;

/**
 * Registers and evaluates world-level Wilderness water controls.
 *
 * <p>The server config is the pack-wide master switch, while the gamerule is
 * saved per world and can be changed at runtime with Minecraft's normal
 * {@code /gamerule} command. Gameplay, migration, and rendering code should
 * ask this helper before taking ownership of water.</p>
 */
public final class WildernessWaterRules {

    /** Enables Wilderness Odyssey water authority, migration, rendering, and local simulation for a world. */
    public static final GameRules.Key<GameRules.BooleanValue> ENABLE_WILDERNESS_ODYSSEY_WATER =
            GameRules.register(
                    "enableWildernessOdysseyWater",
                    GameRules.Category.UPDATES,
                    GameRules.BooleanValue.create(true)
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
        if (!WaterSimulationConfig.wildernessWaterEnabled()) {
            return false;
        }
        return level == null || level.getGameRules().getBoolean(ENABLE_WILDERNESS_ODYSSEY_WATER);
    }
}
