package com.thunder.wildernessodysseyapi.temporalrift.hybrid;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/** A 3-block aperture centered 2.5 blocks above the invisible anchor, with no mesh collision. */
public final class RiftTrigger {
    private RiftTrigger() { }

    public static Vec3 center(BlockPos anchor) {
        return new Vec3(anchor.getX() + 0.5, anchor.getY() + 2.5, anchor.getZ() + 0.5);
    }

    public static AABB bounds(BlockPos anchor, Direction facing) {
        Vec3 center = center(anchor);
        boolean alongX = facing.getAxis() == Direction.Axis.X;
        double x = alongX ? 0.45 : 1.5;
        double z = alongX ? 1.5 : 0.45;
        return new AABB(center.x - x, center.y - 1.5, center.z - z,
                center.x + x, center.y + 1.5, center.z + z);
    }

    /** Sweeps the entity's feet against a Minkowski-expanded aperture to avoid Elytra tunneling. */
    public static boolean crosses(AABB trigger, Vec3 previousFeet, Vec3 feet, double halfWidth, double height) {
        AABB expanded = new AABB(trigger.minX - halfWidth, trigger.minY - height, trigger.minZ - halfWidth,
                trigger.maxX + halfWidth, trigger.maxY, trigger.maxZ + halfWidth);
        return expanded.contains(feet) || expanded.contains(previousFeet) || expanded.clip(previousFeet, feet).isPresent();
    }
}
