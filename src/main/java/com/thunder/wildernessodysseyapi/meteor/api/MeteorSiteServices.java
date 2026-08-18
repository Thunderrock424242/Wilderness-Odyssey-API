package com.thunder.wildernessodysseyapi.meteor.api;

import com.thunder.wildernessodysseyapi.environment.api.EnvironmentServices;
import com.thunder.wildernessodysseyapi.environment.event.WorldDisturbanceService;
import com.thunder.wildernessodysseyapi.environment.event.WorldDisturbanceType;
import com.thunder.wildernessodysseyapi.meteor.worldgen.MeteorSavedData;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

import java.util.Optional;

/**
 * Public authority boundary for persistent meteor-impact sites.
 *
 * <p>Consumers query immutable records through this class rather than scanning
 * crater blocks or iterating the full saved list. Only successful worldgen or
 * entity impacts may publish new sites.</p>
 */
public final class MeteorSiteServices {

    /** Covers the 125-block worldgen crater maximum at the 1.5x radiation scale. */
    private static final int RADIATION_QUERY_RADIUS = 192;

    private MeteorSiteServices() {
    }

    /** Returns the nearest indexed site inside the supplied horizontal radius. */
    public static Optional<MeteorSiteSnapshot> nearest(
            ServerLevel level,
            BlockPos position,
            int maximumDistance
    ) {
        if (level == null || position == null || maximumDistance < 0) {
            return Optional.empty();
        }
        return MeteorSavedData.get(level)
                .findNearest(position, Math.min(4_096, maximumDistance))
                .map(record -> snapshot(record, position));
    }

    /** Returns the strongest normalized radiation at one position. */
    public static double radiationAt(ServerLevel level, BlockPos position) {
        if (level == null || position == null) {
            return 0.0;
        }
        double strongest = 0.0;
        for (MeteorSavedData.MeteorRecord record : MeteorSavedData.get(level)
                .findWithin(position, RADIATION_QUERY_RADIUS)) {
            strongest = Math.max(strongest, radiation(record, position));
        }
        return strongest;
    }

    /**
     * Persists a completed runtime impact and publishes its downstream handoffs.
     *
     * <p>This method must be called only after crater generation succeeds.</p>
     */
    public static MeteorSavedData.MeteorRecord recordImpact(
            ServerLevel level,
            BlockPos center,
            int craterRadius,
            MeteorSiteSource source
    ) {
        MeteorSavedData.MeteorRecord record = MeteorSavedData.get(level).addMeteor(
                center,
                craterRadius,
                level.getGameTime(),
                1.0,
                source
        );
        int influenceRadius = Math.max(48, craterRadius * 4);
        WorldDisturbanceService.publish(
                level,
                center,
                WorldDisturbanceType.METEOR_IMPACT,
                influenceRadius,
                null,
                false
        );
        return record;
    }

    /** Registers a generated crater without creating a runtime panic event. */
    public static MeteorSavedData.MeteorRecord recordGeneratedSite(
            ServerLevel level,
            BlockPos center,
            int craterRadius
    ) {
        MeteorSavedData.MeteorRecord record = MeteorSavedData.get(level).addMeteor(
                center,
                craterRadius,
                level.getGameTime(),
                1.0,
                MeteorSiteSource.WORLDGEN
        );
        EnvironmentServices.invalidate(level, center, Math.max(48, craterRadius * 4));
        return record;
    }

    /** Returns whether story or discovery systems should treat a position as near a site. */
    public static boolean isNearSite(ServerLevel level, BlockPos position, int minimumRadius) {
        int radius = Math.max(0, Math.min(4_096, minimumRadius));
        return MeteorSavedData.get(level).findNearest(position, radius).isPresent();
    }

    private static MeteorSiteSnapshot snapshot(
            MeteorSavedData.MeteorRecord record,
            BlockPos position
    ) {
        double distance = horizontalDistance(record.center(), position);
        return new MeteorSiteSnapshot(
                true,
                record.id(),
                record.center(),
                record.craterRadius(),
                record.createdAt(),
                record.intensity(),
                record.source(),
                distance,
                radiation(record, position)
        );
    }

    private static double radiation(MeteorSavedData.MeteorRecord record, BlockPos position) {
        double radius = Math.max(1.0, record.craterRadius() * 1.5);
        double distance = horizontalDistance(record.center(), position);
        if (distance > radius) {
            return 0.0;
        }
        return Math.max(0.0, Math.min(1.0, record.intensity() * (1.0 - distance / radius)));
    }

    private static double horizontalDistance(BlockPos first, BlockPos second) {
        long dx = (long) first.getX() - second.getX();
        long dz = (long) first.getZ() - second.getZ();
        return Math.sqrt((double) dx * dx + (double) dz * dz);
    }
}
