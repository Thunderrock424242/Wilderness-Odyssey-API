package com.thunder.wildernessodysseyapi.watersystem.water.hydrology;

import com.thunder.wildernessodysseyapi.watersystem.water.volume.WildernessWaterAuthority;
import com.thunder.wildernessodysseyapi.watersystem.water.worldgen.river.RiverChannelColumn;
import com.thunder.wildernessodysseyapi.watersystem.water.worldgen.river.RiverGenerationChunk;
import com.thunder.wildernessodysseyapi.watersystem.water.worldgen.river.RiverReachServices;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;

/** Loaded-only waterfall refinement of the existing authority current; never transfers water. */
public final class WaterfallCurrent {
    private static final int[] X = {0, 1, -1, 0, 0};
    private static final int[] Z = {0, 0, 0, 1, -1};

    private WaterfallCurrent() { }

    /** Samples at most five nearby falls and selects one strongest contribution, rather than summing jets. */
    public static WaterfallHydraulics.Motion sample(Level level, double x, double y, double z) {
        if (!Double.isFinite(x + y + z)) return WaterfallHydraulics.Motion.STILL;
        var best = WaterfallHydraulics.Motion.STILL;
        double strongest = 0;
        BlockPos center = BlockPos.containing(x, y, z);
        for (int index = 0; index < X.length; index++) {
            BlockPos candidate = center.offset(X[index], 0, Z[index]);
            Fall fall = verified(level, candidate);
            if (fall == null) continue;
            var reach = fall.reach();
            var motion = WaterfallHydraulics.sample(reach.topY() + 1, reach.waterY() + 1,
                    x - candidate.getX() - .5, y, z - candidate.getZ() - .5,
                    fall.inflowX(), fall.inflowZ(), fall.fallingY(), level.getGameTime());
            double energy = motion.x() * motion.x() + motion.y() * motion.y() + motion.z() * motion.z();
            if (energy > strongest) { strongest = energy; best = motion; }
        }
        return best;
    }

    /** Validates the entire at-most-17-cell curtain against current authoritative projections. */
    public static Fall verified(Level level, BlockPos position) {
        var metadata = RiverReachServices.sample(level, position);
        if (metadata == null) return null;
        RiverGenerationChunk.Reach reach = metadata.reach();
        if (reach.kind() != RiverChannelColumn.Kind.WATERFALL
                || reach.topY() <= reach.waterY() || reach.topY() - reach.waterY() > 16) return null;
        for (int y = reach.waterY(); y <= reach.topY(); y++) {
            var cell = WildernessWaterAuthority.sample(level, new BlockPos(position.getX(), y, position.getZ()));
            if (!cell.water() || !cell.authorityOwned() || cell.hostedWater() || cell.fillFraction() < .875f) return null;
        }
        BlockPos lip = new BlockPos(position.getX(), reach.topY(), position.getZ());
        var cell = WildernessWaterAuthority.sample(level, lip);
        var regional = WatershedServices.localFlow(level, lip);
        float vx = RiverCurrentField.currentX(cell.velocityX(), cell.velocityZ(), regional.currentX());
        float vz = RiverCurrentField.currentZ(cell.velocityX(), cell.velocityZ(), regional.currentZ());
        if (Math.max(Math.hypot(vx, vz), -cell.velocityY()) < .02) return null;
        return new Fall(reach, vx, vz, Math.min(0, cell.velocityY()));
    }

    /** Geometry and existing physical inflow, with no persistent or numerical volume ownership. */
    public record Fall(RiverGenerationChunk.Reach reach, float inflowX, float inflowZ, float fallingY) { }
}
