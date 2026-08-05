package com.thunder.wildernessodysseyapi.anomaly;

import com.thunder.wildernessodysseyapi.anomaly.registry.AnomalyDimensions;
import com.thunder.wildernessodysseyapi.riftfall.RiftfallStage;
import com.thunder.wildernessodysseyapi.riftfall.RiftfallSystem;
import com.thunder.wildernessodysseyapi.temporalrift.registry.TemporalRiftDimensions;
import com.thunder.wildernessodysseyapi.weather.api.WeatherServices;
import com.thunder.wildernessodysseyapi.weather.config.WeatherConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;

/**
 * Defines the gameplay relationship between the Anomaly, Riftfall, and The Before.
 *
 * <p>Rift creatures are native to the Anomaly and therefore do not depend on
 * weather there. Outside it, the same creatures can manifest only while the
 * local dimension owns an active Riftfall. The Before is connected through
 * gateways, but retains its separate empty-world spawn policy.</p>
 */
public final class AnomalyDimensionRules {
    private AnomalyDimensionRules() {
    }

    /** Returns whether the supplied dimension is the rift creatures' native home. */
    public static boolean isAnomaly(ResourceKey<Level> dimension) {
        return AnomalyDimensions.ANOMALY_DIMENSION_KEY.equals(dimension);
    }

    /** Returns whether an Anomaly Gateway may establish an origin link here. */
    public static boolean isGatewaySource(ResourceKey<Level> dimension) {
        return Level.OVERWORLD.equals(dimension) || TemporalRiftDimensions.THE_BEFORE_KEY.equals(dimension);
    }

    /**
     * Returns whether rift threats should remain manifested in this server level.
     * Native creatures persist in the Anomaly; invasions elsewhere follow the
     * dimension-scoped Riftfall stage.
     */
    public static boolean permitsRiftPresence(ServerLevel level) {
        return permitsRiftPresence(level.dimension(), RiftfallSystem.stage(level));
    }

    /** Resolves rift persistence from an explicit dimension and local storm stage. */
    public static boolean permitsRiftPresence(ResourceKey<Level> dimension, RiftfallStage stage) {
        return isAnomaly(dimension) || stage.isActiveDanger();
    }

    /**
     * Applies the dimension and weather gate shared by natural rift-creature spawns.
     * Normal monster darkness and obstruction rules remain owned by each entity.
     */
    public static boolean permitsNaturalRiftSpawn(ServerLevelAccessor level, BlockPos pos) {
        ServerLevel serverLevel = level.getLevel();
        if (isAnomaly(serverLevel.dimension())) {
            return true;
        }
        if (!RiftfallSystem.stage(serverLevel).isActiveDanger()) {
            return false;
        }
        return WeatherConfig.dimensionEnabled(serverLevel.dimension())
                ? WeatherServices.query().isPrecipitatingAt(serverLevel, pos)
                : serverLevel.isRainingAt(pos);
    }
}
