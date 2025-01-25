package com.thunder.wildernessodysseyapi.ModConflictChecker;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.AABB;

public class StructurePlacementInfo {
    private final ResourceLocation structureName;
    private final ResourceLocation dimensionName;
    private final BlockPos position;
    private final AABB boundingBox;

    public StructurePlacementInfo(ResourceLocation structureName, ResourceLocation dimensionName, BlockPos position) {
        this.structureName = structureName;
        this.dimensionName = dimensionName;
        this.position = position;
        this.boundingBox = new AABB(position.getX(), position.getY(), position.getZ(),
                position.getX() + 16, position.getY() + 16, position.getZ() + 16); // Approximate bounding box
    }

    public ResourceLocation getStructureName() {
        return structureName;
    }

    public ResourceLocation getDimensionName() {
        return dimensionName;
    }

    public BlockPos getPosition() {
        return position;
    }

    public AABB getBoundingBox() {
        return boundingBox;
    }
}
