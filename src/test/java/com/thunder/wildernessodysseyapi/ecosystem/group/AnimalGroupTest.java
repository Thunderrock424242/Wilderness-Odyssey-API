package com.thunder.wildernessodysseyapi.ecosystem.group;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Verifies transient membership, requests, revisions, and leader decision cooldowns. */
class AnimalGroupTest {

    @Test
    void membershipTracksLeaderAndStableFollowerPlans() {
        UUID leader = new UUID(1L, 2L);
        UUID follower = new UUID(3L, 4L);
        AnimalGroup group = group(leader);

        group.addMember(follower);

        assertEquals(2, group.memberCount());
        assertEquals(GroupRole.LEADER, group.roleOf(leader).orElseThrow());
        assertEquals(GroupRole.FOLLOWER, group.roleOf(follower).orElseThrow());
        assertEquals(group.planFor(follower), group.planFor(follower));

        group.setLeader(follower);
        assertEquals(follower, group.getLeader());
        assertEquals(GroupRole.FOLLOWER, group.roleOf(leader).orElseThrow());
    }

    @Test
    void externalDestinationWaitsForLeaderPublication() {
        AnimalGroup group = group(new UUID(5L, 6L));
        BlockPos destination = new BlockPos(20, 70, -12);

        group.requestState(GroupBehavior.SEEK_SHELTER);
        group.requestDestination(destination);

        assertTrue(group.hasPendingLeaderRequest());
        assertEquals(GroupBehavior.SEEK_SHELTER, group.requestedState());
        assertEquals(destination, group.requestedDestination());
        assertEquals(GroupBehavior.IDLE, group.state());

        long revision = group.publishLeaderDecision(
                group.requestedState(), group.requestedDestination(), 100L, 60L);
        assertFalse(group.hasPendingLeaderRequest());
        assertEquals(GroupBehavior.SEEK_SHELTER, group.state());
        assertEquals(destination, group.destination());
        assertFalse(group.canLeaderDecide(159L));
        assertTrue(group.canLeaderDecide(160L));
        assertEquals(1, group.leaderDecisionsPerMinute(160L));

        group.clearDecisionIfRevision(revision);
        assertEquals(GroupBehavior.IDLE, group.state());
        assertNull(group.destination());
    }

    @Test
    void newerThreatCannotBeClearedByAnOlderCompletedDecision() {
        AnimalGroup group = group(new UUID(7L, 8L));
        long olderRevision = group.publishLeaderDecision(
                GroupBehavior.TRAVEL, new BlockPos(5, 64, 5), 20L, 40L);

        group.reportThreat(
                new BlockPos(0, 64, 0),
                new UUID(9L, 10L),
                new BlockPos(16, 64, 0)
        );
        group.clearDecisionIfRevision(olderRevision);

        assertEquals(GroupBehavior.FLEE, group.state());
        assertEquals(new BlockPos(16, 64, 0), group.destination());
        assertTrue(group.hasPendingLeaderRequest());
    }

    private static AnimalGroup group(UUID leader) {
        return new AnimalGroup(
                new UUID(100L, 200L),
                ResourceLocation.fromNamespaceAndPath("wildernessodysseyapi", "configured/test/deer"),
                ResourceLocation.fromNamespaceAndPath("examplemod", "deer"),
                leader,
                200L
        );
    }
}
