package com.thunder.wildernessodysseyapi.watersystem.ocean.tide;

import com.thunder.wildernessodysseyapi.watersystem.ocean.shore.ShorelineWaterManager;
import com.thunder.wildernessodysseyapi.watersystem.water.wave.WaterBodyClassifier;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.level.LevelEvent;

/**
 * Couples the analytic tide to non-destructive shoreline simulation.
 *
 * <p>The previous implementation added and removed source blocks on a four
 * block sampling grid. That produced visible ocean lines and could delete
 * player-placed water. Tides now drive {@link ShorelineWaterManager} state;
 * Minecraft blocks remain untouched.</p>
 */
public final class TideWorldUpdater {

    private TideWorldUpdater() {
    }

    /** Releases bounded region caches when a dimension unloads. */
    @SubscribeEvent
    public static void onLevelUnload(LevelEvent.Unload event) {
        if (event.getLevel() instanceof ServerLevel level) {
            ShorelineWaterManager.get().clearLevel(level);
            WaterBodyClassifier.clearCache(level);
        }
    }
}
