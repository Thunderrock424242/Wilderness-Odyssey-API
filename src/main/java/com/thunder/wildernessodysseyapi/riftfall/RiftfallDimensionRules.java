package com.thunder.wildernessodysseyapi.riftfall;

import com.thunder.wildernessodysseyapi.temporalrift.registry.TemporalRiftDimensions;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

/**
 * Defines which dimensions may host the anomalous Riftfall weather overlay.
 *
 * <p>The rule is shared by server gameplay and client effects so ordinary
 * thunderstorms cannot accidentally gain Riftfall behavior outside The Echo.</p>
 */
public final class RiftfallDimensionRules {
    private RiftfallDimensionRules() {
    }

    /** Returns whether Riftfall weather may run in the supplied dimension. */
    public static boolean isEligible(ResourceKey<Level> dimension) {
        return TemporalRiftDimensions.THE_ECHO_KEY.equals(dimension);
    }

    /**
     * Returns whether active storm presentation may use Riftfall visuals.
     *
     * <p>Keeping the dimension check beside the weather check prevents an
     * ordinary Overworld thunderstorm from inheriting The Echo's purple sky,
     * fog, cloud, and particle treatment.</p>
     */
    public static boolean permitsStormVisuals(
            ResourceKey<Level> dimension,
            boolean precipitationActive,
            boolean thunderActive
    ) {
        return isEligible(dimension) && precipitationActive && thunderActive;
    }
}
