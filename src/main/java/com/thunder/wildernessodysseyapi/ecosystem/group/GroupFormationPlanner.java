package com.thunder.wildernessodysseyapi.ecosystem.group;

import net.minecraft.world.phys.Vec3;

import java.util.UUID;

/**
 * Produces deterministic loose formation slots without querying blocks or creating paths.
 *
 * <p>Golden-angle spacing keeps members from converging on one block. A small,
 * slow drift prevents the resulting shape from looking like a rigid parade
 * formation while remaining predictable enough for inexpensive following.</p>
 */
public final class GroupFormationPlanner {

    private static final double GOLDEN_ANGLE = Math.PI * (3.0 - Math.sqrt(5.0));

    private GroupFormationPlanner() {
    }

    /** Builds stable movement variation for one member and assigned group slot. */
    public static GroupMemberPlan planFor(UUID memberId, int slotIndex) {
        long first = mix64(memberId.getMostSignificantBits() ^ Long.rotateLeft(memberId.getLeastSignificantBits(), 17));
        long second = mix64(first + 0x9E37_79B9_7F4A_7C15L);
        long third = mix64(second + 0xD1B5_4A32_D192_ED03L);
        double radialScale = 0.45 + unit(first) * 0.43;
        double angleJitter = (unit(second) - 0.5) * 0.40;
        double followScale = 0.86 + unit(third) * 0.30;
        int reactionDelay = 2 + (int) Math.floor(unit(mix64(third)) * 13.0);
        int pausePeriod = 150 + (int) Math.floor(unit(mix64(first ^ third)) * 111.0);
        int pauseDuration = 10 + (int) Math.floor(unit(mix64(second ^ third)) * 17.0);
        int pausePhase = (int) Math.floor(unit(mix64(first + second)) * pausePeriod);
        return new GroupMemberPlan(
                Math.max(0, slotIndex),
                radialScale,
                angleJitter,
                followScale,
                reactionDelay,
                pausePeriod,
                pauseDuration,
                pausePhase
        );
    }

    /**
     * Returns a follower target around the leader, oriented along the group's travel direction.
     *
     * @param leaderPosition current leader position
     * @param destination broad destination, or {@code null} while locally idle
     * @param leaderYaw current leader yaw used when no destination supplies a heading
     * @param plan stable member variation
     * @param formationRadius configured maximum loose radius
     * @param gameTime server game time used only for subtle deterministic drift
     * @return an inexpensive relative movement target
     */
    public static Vec3 target(
            Vec3 leaderPosition,
            Vec3 destination,
            float leaderYaw,
            GroupMemberPlan plan,
            double formationRadius,
            long gameTime
    ) {
        Vec3 forward = heading(leaderPosition, destination, leaderYaw);
        Vec3 right = new Vec3(-forward.z, 0.0, forward.x);
        double drift = Math.sin((gameTime + plan.pausePhaseTicks()) * 0.025) * 0.12;
        double angle = plan.slotIndex() * GOLDEN_ANGLE + plan.angleJitter() + drift;
        double radialDrift = Math.sin((gameTime + plan.pausePhaseTicks()) * 0.017) * 0.06;
        double radius = Math.max(0.75, formationRadius * Math.min(0.96, plan.radialScale() + radialDrift));
        double lateral = Math.cos(angle) * radius;
        double depth = Math.sin(angle) * radius;
        return leaderPosition.add(right.scale(lateral)).add(forward.scale(depth));
    }

    private static Vec3 heading(Vec3 leaderPosition, Vec3 destination, float leaderYaw) {
        if (destination != null) {
            Vec3 difference = destination.subtract(leaderPosition).multiply(1.0, 0.0, 1.0);
            if (difference.lengthSqr() > 1.0) {
                return difference.normalize();
            }
        }
        double radians = Math.toRadians(leaderYaw);
        return new Vec3(-Math.sin(radians), 0.0, Math.cos(radians));
    }

    private static long mix64(long value) {
        value = (value ^ (value >>> 30)) * 0xBF58_476D_1CE4_E5B9L;
        value = (value ^ (value >>> 27)) * 0x94D0_49BB_1331_11EBL;
        return value ^ (value >>> 31);
    }

    private static double unit(long value) {
        return (double) (value >>> 11) * 0x1.0p-53;
    }
}
