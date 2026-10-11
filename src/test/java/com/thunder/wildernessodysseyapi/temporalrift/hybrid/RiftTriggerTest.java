package com.thunder.wildernessodysseyapi.temporalrift.hybrid;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class RiftTriggerTest {
    @Test
    void fastElytraCrossingIsDetectedEvenWhenBothEndpointsAreOutside() {
        AABB trigger = RiftTrigger.bounds(BlockPos.ZERO, Direction.NORTH);
        assertTrue(RiftTrigger.crosses(trigger, new Vec3(0.5, 2.5, -8), new Vec3(0.5, 2.5, 8), 0.3, 0.6));
        assertFalse(RiftTrigger.crosses(trigger, new Vec3(5, 2.5, -8), new Vec3(5, 2.5, 8), 0.3, 0.6));
        assertFalse(RiftTrigger.crosses(trigger, new Vec3(0.5, 9, -8), new Vec3(0.5, 9, 8), 0.3, 0.6));
    }

    @Test
    void rotatedApertureUsesTheSameClearOpeningAtNegativeCoordinates() {
        BlockPos pos = new BlockPos(-100, 64, -100);
        AABB trigger = RiftTrigger.bounds(pos, Direction.EAST);
        assertTrue(RiftTrigger.crosses(trigger, new Vec3(-110, 66.5, -99.5), new Vec3(-90, 66.5, -99.5), 0.3, 0.6));
        assertFalse(RiftTrigger.crosses(trigger, new Vec3(-110, 66.5, -94), new Vec3(-90, 66.5, -94), 0.3, 0.6));
        assertEquals(3, trigger.getZsize(), 1.0E-9);
        assertEquals(0.9, trigger.getXsize(), 1.0E-9);
    }
}
