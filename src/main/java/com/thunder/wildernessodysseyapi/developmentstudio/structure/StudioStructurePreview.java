package com.thunder.wildernessodysseyapi.developmentstudio.structure;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.phys.AABB;

/** Server-computed transformed bounds for one player's current structure preview. */
public record StudioStructurePreview(
        ResourceLocation structureId,
        ResourceLocation dimension,
        BlockPos origin,
        BlockPos min,
        BlockPos max,
        Rotation rotation,
        Mirror mirror,
        long expiresAtGameTime
) {
    public AABB bounds() {
        return new AABB(min.getX(), min.getY(), min.getZ(),
                max.getX() + 1.0D, max.getY() + 1.0D, max.getZ() + 1.0D);
    }
}
