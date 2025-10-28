package com.thunder.wildernessodysseyapi.mixin;

import com.thunder.wildernessodysseyapi.util.StructureBlockSettings;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.entity.StructureBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.StructureBlock;
import net.minecraft.world.level.block.state.properties.StructureMode;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Expands the structure block capture size and automatically snaps the save area to the occupied blocks.
 */
@Mixin(StructureBlockEntity.class)
public abstract class StructureBlockEntityMixin extends BlockEntity {

    @Shadow @Final @Mutable private static int MAX_OFFSET_PER_AXIS;
    @Shadow @Final @Mutable private static int MAX_SIZE_PER_AXIS;

    @Shadow private BlockPos structurePos;
    @Shadow private Vec3i structureSize;
    @Shadow @org.jetbrains.annotations.Nullable private ResourceLocation structureName;

    @Shadow public abstract void setStructurePos(BlockPos pos);
    @Shadow public abstract void setStructureSize(Vec3i size);

    @Shadow public abstract StructureMode getMode();
    @Shadow @org.jetbrains.annotations.Nullable public abstract ResourceLocation getStructureName();
    @Shadow public abstract void setMode(StructureMode mode);
    @Shadow public abstract void setStructureName(ResourceLocation name);

    protected StructureBlockEntityMixin(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    @Unique
    private static boolean wildernessodysseyapi$limitsExpanded;

    @Unique
    private final java.util.Set<BlockPos> wildernessodysseyapi$cornerMarkers = new java.util.HashSet<>();

    @Inject(method = "<init>", at = @At("TAIL"))
    private void wildernessodysseyapi$expandLimits(BlockPos pos, BlockState state, CallbackInfo ci) {
        if (wildernessodysseyapi$limitsExpanded) {
            return;
        }
        wildernessodysseyapi$limitsExpanded = true;
        MAX_SIZE_PER_AXIS = StructureBlockSettings.MAX_STRUCTURE_SIZE;
        MAX_OFFSET_PER_AXIS = StructureBlockSettings.MAX_STRUCTURE_OFFSET;
    }

    @Inject(method = "detectSize", at = @At("HEAD"), cancellable = true)
    private void wildernessodysseyapi$scanSurroundingBlocks(CallbackInfoReturnable<Boolean> cir) {
        Level level = this.level;
        if (!(level instanceof ServerLevel serverLevel)) {
            return;
        }
        if (this.getMode() != StructureMode.SAVE) {
            return;
        }

        BlockPos blockPos = this.getBlockPos();
        ResourceLocation structureName = this.getStructureName();
        String structureNameKey = structureName == null ? null : structureName.toString();
        BlockPos currentOffset = this.structurePos == null ? BlockPos.ZERO : this.structurePos;
        Vec3i currentSize = this.structureSize == null ? Vec3i.ZERO : this.structureSize;

        int detectionRadius = StructureBlockSettings.DEFAULT_DETECTION_RADIUS;
        detectionRadius = Math.max(detectionRadius, wildernessodysseyapi$computeRadiusForAxis(currentOffset.getX(), currentSize.getX()));
        detectionRadius = Math.max(detectionRadius, wildernessodysseyapi$computeRadiusForAxis(currentOffset.getY(), currentSize.getY()));
        detectionRadius = Math.max(detectionRadius, wildernessodysseyapi$computeRadiusForAxis(currentOffset.getZ(), currentSize.getZ()));
        detectionRadius = Math.min(detectionRadius, StructureBlockSettings.MAX_STRUCTURE_OFFSET);
        if (detectionRadius <= 0) {
            return;
        }

        int minXBound = blockPos.getX() - detectionRadius;
        int maxXBound = blockPos.getX() + detectionRadius;
        int minZBound = blockPos.getZ() - detectionRadius;
        int maxZBound = blockPos.getZ() + detectionRadius;
        int minYBound = Math.max(serverLevel.getMinBuildHeight(), blockPos.getY() - detectionRadius);
        int maxYBound = Math.min(serverLevel.getMaxBuildHeight() - 1, blockPos.getY() + detectionRadius);

        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        boolean hasBounds = false;
        java.util.List<BlockPos> cornerMarkers = new java.util.ArrayList<>();
        int minX = 0;
        int minY = 0;
        int minZ = 0;
        int maxX = 0;
        int maxY = 0;
        int maxZ = 0;

        for (int x = minXBound; x <= maxXBound; x++) {
            for (int y = minYBound; y <= maxYBound; y++) {
                for (int z = minZBound; z <= maxZBound; z++) {
                    cursor.set(x, y, z);
                    BlockState blockState = serverLevel.getBlockState(cursor);
                    if (structureNameKey != null && blockState.is(Blocks.STRUCTURE_BLOCK)) {
                        BlockEntity entity = serverLevel.getBlockEntity(cursor);
                        if (entity instanceof StructureBlockEntity structureBlockEntity
                                && structureBlockEntity.getMode() == StructureMode.CORNER) {
                            String otherName = structureBlockEntity.getStructureName();
                            if (otherName != null && otherName.equals(structureNameKey)) {
                                cornerMarkers.add(cursor.immutable());
                            }
                        }
                    }
                    if (!StructureBlockSettings.isStructureContent(blockState)) {
                        continue;
                    }
                    if (cursor.equals(blockPos)) {
                        continue;
                    }
                    if (!hasBounds) {
                        hasBounds = true;
                        minX = maxX = x;
                        minY = maxY = y;
                        minZ = maxZ = z;
                        continue;
                    }
                    if (x < minX) {
                        minX = x;
                    }
                    if (y < minY) {
                        minY = y;
                    }
                    if (z < minZ) {
                        minZ = z;
                    }
                    if (x > maxX) {
                        maxX = x;
                    }
                    if (y > maxY) {
                        maxY = y;
                    }
                    if (z > maxZ) {
                        maxZ = z;
                    }
                }
            }
        }

        if (!cornerMarkers.isEmpty()) {
            for (BlockPos corner : cornerMarkers) {
                if (corner.equals(blockPos)) {
                    continue;
                }
                int x = corner.getX();
                int y = corner.getY();
                int z = corner.getZ();
                if (!hasBounds) {
                    hasBounds = true;
                    minX = maxX = x;
                    minY = maxY = y;
                    minZ = maxZ = z;
                    continue;
                }
                if (x < minX) {
                    minX = x;
                }
                if (y < minY) {
                    minY = y;
                }
                if (z < minZ) {
                    minZ = z;
                }
                if (x > maxX) {
                    maxX = x;
                }
                if (y > maxY) {
                    maxY = y;
                }
                if (z > maxZ) {
                    maxZ = z;
                }
            }
        }

        if (!hasBounds) {
            return;
        }

        BlockPos newStart = new BlockPos(minX, minY, minZ);
        BlockPos newSize = new BlockPos(maxX - minX + 1, maxY - minY + 1, maxZ - minZ + 1);
        BlockPos relativePos = newStart.subtract(blockPos);

        boolean changed = false;
        if (!relativePos.equals(this.structurePos)) {
            this.setStructurePos(relativePos);
            changed = true;
        }
        if (!newSize.equals(this.structureSize)) {
            this.setStructureSize(newSize);
            changed = true;
        }

        if (changed) {
            this.setChanged();
            BlockState state = this.getBlockState();
            serverLevel.sendBlockUpdated(blockPos, state, state, 3);
        }

        wildernessodysseyapi$placeCornerBlocks(serverLevel, blockPos, newStart, newSize);

        if (!changed) {
            cir.setReturnValue(true);
            cir.cancel();
            return;
        }

        cir.setReturnValue(true);
        cir.cancel();
    }

    @Inject(method = "saveStructure", at = @At("HEAD"))
    private void wildernessodysseyapi$autoFitStructure(CallbackInfoReturnable<Boolean> cir) {
        Level level = this.level;
        if (!(level instanceof ServerLevel serverLevel)) {
            return;
        }
        if (this.getMode() != StructureMode.SAVE) {
            return;
        }

        if (this.structureSize.getX() <= 0 || this.structureSize.getY() <= 0 || this.structureSize.getZ() <= 0) {
            return;
        }

        BlockPos blockPos = this.getBlockPos();
        BlockPos start = blockPos.offset(this.structurePos);
        BlockPos end = start.offset(this.structureSize.getX() - 1, this.structureSize.getY() - 1, this.structureSize.getZ() - 1);

        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        boolean found = false;
        int minX = Integer.MAX_VALUE;
        int minY = Integer.MAX_VALUE;
        int minZ = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE;
        int maxY = Integer.MIN_VALUE;
        int maxZ = Integer.MIN_VALUE;

        for (int x = start.getX(); x <= end.getX(); x++) {
            for (int y = start.getY(); y <= end.getY(); y++) {
                for (int z = start.getZ(); z <= end.getZ(); z++) {
                    cursor.set(x, y, z);
                    if (!StructureBlockSettings.isStructureContent(serverLevel.getBlockState(cursor))) {
                        continue;
                    }
                    if (cursor.equals(blockPos)) {
                        continue;
                    }
                    found = true;
                    if (x < minX) {
                        minX = x;
                    }
                    if (y < minY) {
                        minY = y;
                    }
                    if (z < minZ) {
                        minZ = z;
                    }
                    if (x > maxX) {
                        maxX = x;
                    }
                    if (y > maxY) {
                        maxY = y;
                    }
                    if (z > maxZ) {
                        maxZ = z;
                    }
                }
            }
        }

        if (!found) {
            return;
        }

        BlockPos newStart = new BlockPos(minX, minY, minZ);
        BlockPos newSize = new BlockPos(maxX - minX + 1, maxY - minY + 1, maxZ - minZ + 1);
        BlockPos relativePos = newStart.subtract(blockPos);

        if (!relativePos.equals(this.structurePos)) {
            this.setStructurePos(relativePos);
        }
        if (!newSize.equals(this.structureSize)) {
            this.setStructureSize(newSize);
        }
    }

    @Unique
    private static int wildernessodysseyapi$computeRadiusForAxis(int offset, int size) {
        if (size <= 0) {
            return Math.abs(offset);
        }
        int start = offset;
        int end = offset + size - 1;
        return Math.max(Math.abs(start), Math.abs(end));
    }

    @Unique
    private void wildernessodysseyapi$placeCornerBlocks(ServerLevel serverLevel, BlockPos structureBlockPos, BlockPos minCorner, Vec3i size) {
        ResourceLocation structureName = this.getStructureName();
        if (structureName == null) {
            return;
        }

        BlockPos maxCorner = minCorner.offset(size.getX() - 1, size.getY() - 1, size.getZ() - 1);
        java.util.List<BlockPos> bottomCorners = new java.util.ArrayList<>(4);
        bottomCorners.add(new BlockPos(minCorner.getX(), minCorner.getY(), minCorner.getZ()));
        bottomCorners.add(new BlockPos(maxCorner.getX(), minCorner.getY(), minCorner.getZ()));
        bottomCorners.add(new BlockPos(minCorner.getX(), minCorner.getY(), maxCorner.getZ()));
        bottomCorners.add(new BlockPos(maxCorner.getX(), minCorner.getY(), maxCorner.getZ()));

        bottomCorners.sort(java.util.Comparator.comparingInt(pos -> pos.distManhattan(structureBlockPos)));

        java.util.LinkedHashSet<BlockPos> desiredCorners = new java.util.LinkedHashSet<>();

        for (int i = 1; i < bottomCorners.size() && desiredCorners.size() < 2; i++) {
            BlockPos candidate = bottomCorners.get(i);
            if (!candidate.equals(structureBlockPos)) {
                desiredCorners.add(candidate);
            }
        }

        if (size.getY() > 1) {
            BlockPos topCorner = new BlockPos(maxCorner.getX(), maxCorner.getY(), maxCorner.getZ());
            if (!topCorner.equals(structureBlockPos)) {
                desiredCorners.add(topCorner);
            }
        }

        if (desiredCorners.isEmpty()) {
            return;
        }

        java.util.Set<BlockPos> retainedMarkers = new java.util.HashSet<>();

        for (BlockPos target : desiredCorners) {
            retainedMarkers.add(target.immutable());
        }

        java.util.Iterator<BlockPos> existing = this.wildernessodysseyapi$cornerMarkers.iterator();
        while (existing.hasNext()) {
            BlockPos tracked = existing.next();
            if (retainedMarkers.contains(tracked)) {
                continue;
            }
            BlockState state = serverLevel.getBlockState(tracked);
            if (state.is(Blocks.STRUCTURE_BLOCK)) {
                BlockEntity entity = serverLevel.getBlockEntity(tracked);
                if (entity instanceof StructureBlockEntity structureBlockEntity && structureBlockEntity.getMode() == StructureMode.CORNER) {
                    serverLevel.setBlock(tracked, Blocks.AIR.defaultBlockState(), 3);
                }
            }
            existing.remove();
        }

        BlockState cornerState = Blocks.STRUCTURE_BLOCK.defaultBlockState().setValue(StructureBlock.MODE, StructureMode.CORNER);

        for (BlockPos target : desiredCorners) {
            if (!serverLevel.hasChunkAt(target)) {
                continue;
            }
            BlockState state = serverLevel.getBlockState(target);
            if (!state.isAir() && !state.is(Blocks.STRUCTURE_BLOCK) && !state.is(Blocks.STRUCTURE_VOID)) {
                continue;
            }
            if (!state.is(cornerState.getBlock())) {
                serverLevel.setBlock(target, cornerState, 3);
            }
            BlockEntity entity = serverLevel.getBlockEntity(target);
            if (entity instanceof StructureBlockEntity structureBlockEntity) {
                structureBlockEntity.setStructureName(structureName);
                structureBlockEntity.setMode(StructureMode.CORNER);
                structureBlockEntity.setStructurePos(BlockPos.ZERO);
                structureBlockEntity.setStructureSize(Vec3i.ZERO);
                structureBlockEntity.setChanged();
            }
            this.wildernessodysseyapi$cornerMarkers.add(target.immutable());
        }
    }
}