package com.thunder.wildernessodysseyapi.ecosystem.group;

import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Verifies natural follower variation remains deterministic, bounded, and non-bunched. */
class GroupFormationPlannerTest {

    @Test
    void memberPlanIsStableAndWithinConfiguredVariationBounds() {
        UUID member = new UUID(123L, 456L);

        GroupMemberPlan first = GroupFormationPlanner.planFor(member, 3);
        GroupMemberPlan second = GroupFormationPlanner.planFor(member, 3);

        assertEquals(first, second);
        assertTrue(first.followDistanceScale() >= 0.86 && first.followDistanceScale() <= 1.16);
        assertTrue(first.reactionDelayTicks() >= 2 && first.reactionDelayTicks() <= 14);
        assertTrue(first.pauseDurationTicks() < first.pausePeriodTicks());
    }

    @Test
    void goldenAngleSlotsDoNotCollapseFollowersOntoOneBlock() {
        Vec3 leader = new Vec3(10.0, 64.0, 10.0);
        Vec3 destination = new Vec3(40.0, 64.0, 10.0);
        Set<String> roundedTargets = new HashSet<>();

        for (int slot = 1; slot <= 12; slot++) {
            GroupMemberPlan plan = GroupFormationPlanner.planFor(new UUID(slot, slot * 31L), slot);
            Vec3 target = GroupFormationPlanner.target(leader, destination, 0.0F, plan, 6.0, 200L);
            double horizontalDistance = target.multiply(1.0, 0.0, 1.0)
                    .distanceTo(leader.multiply(1.0, 0.0, 1.0));
            assertTrue(horizontalDistance >= 0.75 && horizontalDistance <= 6.0);
            roundedTargets.add(Math.round(target.x) + ":" + Math.round(target.z));
        }

        assertTrue(roundedTargets.size() >= 9, "most followers should occupy distinct block-scale slots");
    }

    @Test
    void differentSlotsProduceDifferentTargets() {
        Vec3 leader = Vec3.ZERO;
        GroupMemberPlan first = GroupFormationPlanner.planFor(new UUID(1L, 1L), 1);
        GroupMemberPlan second = GroupFormationPlanner.planFor(new UUID(2L, 2L), 2);

        Vec3 firstTarget = GroupFormationPlanner.target(leader, null, 90.0F, first, 5.0, 0L);
        Vec3 secondTarget = GroupFormationPlanner.target(leader, null, 90.0F, second, 5.0, 0L);

        assertNotEquals(firstTarget, secondTarget);
    }
}
