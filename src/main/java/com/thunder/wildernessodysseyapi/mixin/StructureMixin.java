package com.thunder.wildernessodysseyapi.mixin;

import com.thunder.wildernessodysseyapi.ModConflictChecker.StructurePlacementInfo;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.phys.AABB;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.HashSet;
import java.util.Set;

import static com.thunder.wildernessodysseyapi.MainModClass.WildernessOdysseyAPIMainModClass.LOGGER;

@Mixin(Structure.class)
public class StructureMixin {

    private static final Set<StructurePlacementInfo> placedStructures = new HashSet<>();

    @Inject(method = "place", at = @At("HEAD"))
    private void onStructurePlace(Structure.GenerationContext context, CallbackInfoReturnable<Boolean> cir) {
        Structure structure = (Structure) (Object) this; // Access the structure instance
        ResourceLocation structureName = structure.getRegistryName();
        ResourceLocation dimensionName = context.level().dimension().location();
        ChunkPos chunkPos = context.chunkPos();
        BlockPos position = chunkPos.getWorldPosition();

        LOGGER.info("Placing structure '{}' at {} in dimension '{}'", structureName, position, dimensionName);

        adjustTerrain(context, position);

        if (isOverlapping(position, structure.getBoundingBoxForChunk(context))) {
            LOGGER.error("Conflict detected! Structure '{}' overlaps with another structure at {} in dimension '{}'.",
                    structureName, position, dimensionName);
        } else {
            placedStructures.add(new StructurePlacementInfo(structureName, dimensionName, position));
        }
    }

    private void adjustTerrain(Structure.GenerationContext context, BlockPos position) {
        var level = context.level();
        var boundingBox = context.structure().getBoundingBoxForChunk(context);

        LOGGER.info("Adjusting terrain for structure at {} with bounding box {}", position, boundingBox);

        int minX = (int) boundingBox.minX;
        int maxX = (int) boundingBox.maxX;
        int minZ = (int) boundingBox.minZ;
        int maxZ = (int) boundingBox.maxZ;

        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                BlockPos targetPos = new BlockPos(x, position.getY(), z);
                int terrainHeight = level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.WORLD_SURFACE, x, z);

                for (int y = terrainHeight; y < position.getY(); y++) {
                    BlockPos fillPos = new BlockPos(x, y, z);
                    if (level.getBlockState(fillPos).isAir()) {
                        level.setBlock(fillPos, net.minecraft.world.level.block.Blocks.DIRT.defaultBlockState(), 3);
                    }
                }
            }
        }
    }

    private boolean isOverlapping(BlockPos position, AABB boundingBox) {
        return placedStructures.stream().anyMatch(info -> info.getBoundingBox().intersects(boundingBox));
    }
}
