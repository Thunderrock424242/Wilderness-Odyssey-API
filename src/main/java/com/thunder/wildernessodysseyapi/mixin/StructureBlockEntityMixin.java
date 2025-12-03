package com.thunder.wildernessodysseyapi.mixin;

import com.thunder.wildernessodysseyapi.bridge.StructureBlockCornerCacheBridge;
import com.thunder.wildernessodysseyapi.util.StructureBlockCornerCache;
import com.thunder.wildernessodysseyapi.util.NbtCompressionUtils;
import com.thunder.wildernessodysseyapi.util.StructureBlockSettings;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.Vec3i;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerChunkCache;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.entity.StructureBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.StructureBlock;
import net.minecraft.world.level.block.state.properties.StructureMode;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.util.Mth;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.storage.LevelResource;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.nio.file.Files;

/**
 * Expands the structure block capture size and automatically snaps the save area to the occupied blocks.
 */
@Mixin(StructureBlockEntity.class)
public abstract class StructureBlockEntityMixin extends BlockEntity implements StructureBlockCornerCacheBridge {

    @Shadow @Final @Mutable
    public static int MAX_OFFSET_PER_AXIS;
    @Shadow @Final @Mutable
    public static int MAX_SIZE_PER_AXIS;

    @Shadow private BlockPos structurePos;
    @Shadow private Vec3i structureSize;
    @Shadow @org.jetbrains.annotations.Nullable private ResourceLocation structureName;
    @Shadow @org.jetbrains.annotations.Nullable public abstract String getStructureName();

    @Shadow public abstract void setStructurePos(BlockPos pos);
    @Shadow public abstract void setStructureSize(Vec3i size);

    @Shadow public abstract StructureMode getMode();
    @Shadow public abstract void setMode(StructureMode mode);
    @Shadow public abstract void setStructureName(ResourceLocation name);

    protected StructureBlockEntityMixin(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    @Unique
    private final java.util.Set<BlockPos> wildernessodysseyapi$cornerMarkers = new java.util.HashSet<>();
    @Unique
    private boolean wildernessodysseyapi$cacheRegistered;
    @Unique
    private @org.jetbrains.annotations.Nullable String wildernessodysseyapi$cachedCornerName;
    @Unique
    private @org.jetbrains.annotations.Nullable ServerLevel wildernessodysseyapi$cachedCornerLevel;

    @Inject(method = "loadAdditional", at = @At("TAIL"))
    private void wildernessodysseyapi$handleLoad(CompoundTag tag, HolderLookup.Provider lookupProvider, CallbackInfo ci) {
        wildernessodysseyapi$syncCornerCache();
    }

    @Inject(method = "setMode", at = @At("TAIL"))
    private void wildernessodysseyapi$handleModeUpdate(StructureMode mode, CallbackInfo ci) {
        wildernessodysseyapi$syncCornerCache();
    }

    @Inject(method = "setStructureName(Lnet/minecraft/resources/ResourceLocation;)V", at = @At("TAIL"))
    private void wildernessodysseyapi$handleNameUpdate(@org.jetbrains.annotations.Nullable ResourceLocation name,
            CallbackInfo ci) {
        wildernessodysseyapi$syncCornerCache();
    }

    @Inject(method = "<init>", at = @At("TAIL"))
    private void wildernessodysseyapi$expandLimits(BlockPos pos, BlockState state, CallbackInfo ci) {
        int configuredSize = StructureBlockSettings.getMaxStructureSize();
        int configuredOffset = StructureBlockSettings.getMaxStructureOffset();
        if (MAX_SIZE_PER_AXIS != configuredSize) {
            MAX_SIZE_PER_AXIS = configuredSize;
        }
        if (MAX_OFFSET_PER_AXIS != configuredOffset) {
            MAX_OFFSET_PER_AXIS = configuredOffset;
        }
    }

    @Redirect(method = "loadAdditional", at = @At(value = "INVOKE", target = "Lnet/minecraft/util/Mth;clamp(III)I"))
    private int wildernessodysseyapi$expandClampRange(int value, int min, int max) {
        if (min < 0) {
            int limit = StructureBlockSettings.getMaxStructureOffset();
            return Mth.clamp(value, -limit, limit);
        }
        int limit = StructureBlockSettings.getMaxStructureSize();
        return Mth.clamp(value, 0, limit);
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
        int structureX = blockPos.getX();
        int structureY = blockPos.getY();
        int structureZ = blockPos.getZ();
        String structureNameKey = wildernessodysseyapi$normalizeStructureName(this.getStructureName());
        BlockPos currentOffset = this.structurePos == null ? BlockPos.ZERO : this.structurePos;
        Vec3i currentSize = this.structureSize == null ? Vec3i.ZERO : this.structureSize;

        int detectionRadius = StructureBlockSettings.getDefaultDetectionRadius();
        detectionRadius = Math.max(detectionRadius, wildernessodysseyapi$computeRadiusForAxis(currentOffset.getX(), currentSize.getX()));
        detectionRadius = Math.max(detectionRadius, wildernessodysseyapi$computeRadiusForAxis(currentOffset.getY(), currentSize.getY()));
        detectionRadius = Math.max(detectionRadius, wildernessodysseyapi$computeRadiusForAxis(currentOffset.getZ(), currentSize.getZ()));
        detectionRadius = Math.min(detectionRadius, StructureBlockSettings.getMaxStructureOffset());
        if (detectionRadius <= 0) {
            return;
        }

        int minXBound = blockPos.getX() - detectionRadius;
        int maxXBound = blockPos.getX() + detectionRadius;
        int minZBound = blockPos.getZ() - detectionRadius;
        int maxZBound = blockPos.getZ() + detectionRadius;
        int minYBound = Math.max(serverLevel.getMinBuildHeight(), blockPos.getY() - detectionRadius);
        int maxYBound = Math.min(serverLevel.getMaxBuildHeight() - 1, blockPos.getY() + detectionRadius);

        wildernessodysseyapi$warmupChunks(serverLevel, minXBound, maxXBound, minZBound, maxZBound);

        java.util.List<BlockPos> cornerMarkers = new java.util.ArrayList<>();
        java.util.Set<BlockPos> knownCorners = new java.util.HashSet<>();
        if (structureNameKey != null) {
            StructureBlockCornerCache cache = StructureBlockCornerCache.getIfPresent(serverLevel);
            if (cache != null) {
                java.util.List<BlockPos> cachedCorners = cache.findCorners(structureNameKey, blockPos, detectionRadius);
                for (BlockPos cachedCorner : cachedCorners) {
                    if (cachedCorner.equals(blockPos)) {
                        continue;
                    }
                    if (knownCorners.contains(cachedCorner)) {
                        continue;
                    }
                    java.lang.Boolean validation = wildernessodysseyapi$validateCorner(serverLevel, cachedCorner,
                            structureNameKey);
                    if (java.lang.Boolean.FALSE.equals(validation)) {
                        cache.removeCorner(cachedCorner);
                        continue;
                    }
                    if (validation == null) {
                        continue;
                    }
                    knownCorners.add(cachedCorner);
                    cornerMarkers.add(cachedCorner);
                }
            }
            if (cornerMarkers.isEmpty()) {
                wildernessodysseyapi$scanCornersInCube(serverLevel, blockPos, structureNameKey, minXBound, maxXBound,
                        minYBound, maxYBound, minZBound, maxZBound, cornerMarkers, knownCorners);
            }
        }

        wildernessodysseyapi$collectFarCorners(serverLevel, blockPos, structureNameKey, cornerMarkers, knownCorners,
                detectionRadius);

        boolean restrictToCornerBounds = false;
        int cornerBoundMinX = structureX;
        int cornerBoundMaxX = structureX;
        int cornerBoundMinY = structureY;
        int cornerBoundMaxY = structureY;
        int cornerBoundMinZ = structureZ;
        int cornerBoundMaxZ = structureZ;
        boolean cornerHasVerticalExtent = false;

        for (BlockPos corner : cornerMarkers) {
            if (corner.equals(blockPos)) {
                continue;
            }
            restrictToCornerBounds = true;
            int cornerX = corner.getX();
            int cornerY = corner.getY();
            int cornerZ = corner.getZ();
            if (cornerX < cornerBoundMinX) {
                cornerBoundMinX = cornerX;
            }
            if (cornerX > cornerBoundMaxX) {
                cornerBoundMaxX = cornerX;
            }
            if (cornerY < cornerBoundMinY) {
                cornerBoundMinY = cornerY;
            }
            if (cornerY > cornerBoundMaxY) {
                cornerBoundMaxY = cornerY;
            }
            if (cornerY != structureY) {
                cornerHasVerticalExtent = true;
            }
            if (cornerZ < cornerBoundMinZ) {
                cornerBoundMinZ = cornerZ;
            }
            if (cornerZ > cornerBoundMaxZ) {
                cornerBoundMaxZ = cornerZ;
            }
        }

        if (restrictToCornerBounds && !cornerHasVerticalExtent) {
            cornerBoundMinY = minYBound;
            cornerBoundMaxY = maxYBound;
        }

        java.util.Set<BlockPos> visited = new java.util.HashSet<>();
        java.util.ArrayDeque<BlockPos> queue = new java.util.ArrayDeque<>();
        boolean hasBounds = false;
        int minX = 0;
        int minY = 0;
        int minZ = 0;
        int maxX = 0;
        int maxY = 0;
        int maxZ = 0;

        for (Direction direction : Direction.values()) {
            BlockPos neighbor = blockPos.relative(direction);
            if (neighbor.getX() < minXBound || neighbor.getX() > maxXBound) {
                continue;
            }
            if (neighbor.getY() < minYBound || neighbor.getY() > maxYBound) {
                continue;
            }
            if (neighbor.getZ() < minZBound || neighbor.getZ() > maxZBound) {
                continue;
            }
            if (restrictToCornerBounds) {
                if (neighbor.getX() < cornerBoundMinX || neighbor.getX() > cornerBoundMaxX) {
                    continue;
                }
                if (neighbor.getY() < cornerBoundMinY || neighbor.getY() > cornerBoundMaxY) {
                    continue;
                }
                if (neighbor.getZ() < cornerBoundMinZ || neighbor.getZ() > cornerBoundMaxZ) {
                    continue;
                }
            }
            BlockState neighborState = serverLevel.getBlockState(neighbor);
            if (!StructureBlockSettings.isStructureContent(neighborState)) {
                continue;
            }
            if (!visited.add(neighbor.immutable())) {
                continue;
            }
            queue.addLast(neighbor.immutable());
        }

        boolean contentBelowX = false;
        boolean contentAboveX = false;
        boolean contentBelowY = false;
        boolean contentAboveY = false;
        boolean contentBelowZ = false;
        boolean contentAboveZ = false;

        while (!queue.isEmpty()) {
            BlockPos current = queue.removeFirst();
            int x = current.getX();
            int y = current.getY();
            int z = current.getZ();

            if (x < structureX) {
                contentBelowX = true;
            } else if (x > structureX) {
                contentAboveX = true;
            }
            if (y < structureY) {
                contentBelowY = true;
            } else if (y > structureY) {
                contentAboveY = true;
            }
            if (z < structureZ) {
                contentBelowZ = true;
            } else if (z > structureZ) {
                contentAboveZ = true;
            }
            if (!hasBounds) {
                hasBounds = true;
                minX = maxX = x;
                minY = maxY = y;
                minZ = maxZ = z;
            } else {
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

            for (Direction direction : Direction.values()) {
                BlockPos next = current.relative(direction);
                if (next.equals(blockPos)) {
                    continue;
                }
                if (Math.abs(next.getX() - blockPos.getX()) > detectionRadius
                        || Math.abs(next.getY() - blockPos.getY()) > detectionRadius
                        || Math.abs(next.getZ() - blockPos.getZ()) > detectionRadius) {
                    continue;
                }
                if (restrictToCornerBounds) {
                    if (next.getX() < cornerBoundMinX || next.getX() > cornerBoundMaxX) {
                        continue;
                    }
                    if (next.getY() < cornerBoundMinY || next.getY() > cornerBoundMaxY) {
                        continue;
                    }
                    if (next.getZ() < cornerBoundMinZ || next.getZ() > cornerBoundMaxZ) {
                        continue;
                    }
                }
                if (next.getY() < minYBound || next.getY() > maxYBound) {
                    continue;
                }
                if (!visited.add(next.immutable())) {
                    continue;
                }
                BlockState state = serverLevel.getBlockState(next);
                if (!StructureBlockSettings.isStructureContent(state)) {
                    continue;
                }
                queue.addLast(next.immutable());
            }
        }

        boolean cornerBelowX = false;
        boolean cornerAboveX = false;
        boolean cornerBelowY = false;
        boolean cornerAboveY = false;
        boolean cornerBelowZ = false;
        boolean cornerAboveZ = false;

        if (!cornerMarkers.isEmpty()) {
            for (BlockPos corner : cornerMarkers) {
                if (corner.equals(blockPos)) {
                    continue;
                }
                int x = corner.getX();
                int y = corner.getY();
                int z = corner.getZ();

                if (x < structureX) {
                    cornerBelowX = true;
                } else if (x > structureX) {
                    cornerAboveX = true;
                }
                if (y < structureY) {
                    cornerBelowY = true;
                } else if (y > structureY) {
                    cornerAboveY = true;
                }
                if (z < structureZ) {
                    cornerBelowZ = true;
                } else if (z > structureZ) {
                    cornerAboveZ = true;
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

        if (hasBounds && !cornerMarkers.isEmpty()) {
            if (!cornerBelowX && cornerAboveX && !contentBelowX) {
                minX = structureX;
            } else if (!cornerAboveX && cornerBelowX && !contentAboveX) {
                maxX = structureX;
            }
            if (!cornerBelowY && cornerAboveY && !contentBelowY) {
                minY = structureY;
            } else if (!cornerAboveY && cornerBelowY && !contentAboveY) {
                maxY = structureY;
            }
            if (!cornerBelowZ && cornerAboveZ && !contentBelowZ) {
                minZ = structureZ;
            } else if (!cornerAboveZ && cornerBelowZ && !contentAboveZ) {
                maxZ = structureZ;
            }
        }

        if (hasBounds) {
            if (structureX < minX) {
                minX = structureX;
            }
            if (structureY < minY) {
                minY = structureY;
            }
            if (structureZ < minZ) {
                minZ = structureZ;
            }
            if (structureX > maxX) {
                maxX = structureX;
            }
            if (structureY > maxY) {
                maxY = structureY;
            }
            if (structureZ > maxZ) {
                maxZ = structureZ;
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
    private void wildernessodysseyapi$scanCornersInCube(ServerLevel serverLevel, BlockPos origin, String structureNameKey,
            int minX, int maxX, int minY, int maxY, int minZ, int maxZ, java.util.List<BlockPos> cornerMarkers,
            java.util.Set<BlockPos> knownCorners) {
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        StructureBlockCornerCache cache = StructureBlockCornerCache.get(serverLevel);

        for (int x = minX; x <= maxX; x++) {
            for (int y = minY; y <= maxY; y++) {
                for (int z = minZ; z <= maxZ; z++) {
                    cursor.set(x, y, z);
                    if (cursor.equals(origin)) {
                        continue;
                    }
                    java.lang.Boolean validation = wildernessodysseyapi$validateCorner(serverLevel, cursor,
                            structureNameKey);
                    if (!java.lang.Boolean.TRUE.equals(validation)) {
                        continue;
                    }
                    BlockPos immutable = cursor.immutable();
                    if (!knownCorners.add(immutable)) {
                        continue;
                    }
                    cornerMarkers.add(immutable);
                    cache.addCorner(immutable, structureNameKey);
                }
            }
        }
    }

    @Unique
    private java.lang.Boolean wildernessodysseyapi$validateCorner(ServerLevel serverLevel, BlockPos position,
            String structureNameKey) {
        if (!serverLevel.hasChunkAt(position)) {
            return null;
        }
        BlockState blockState = serverLevel.getBlockState(position);
        if (!blockState.is(Blocks.STRUCTURE_BLOCK)) {
            return Boolean.FALSE;
        }
        BlockEntity entity = serverLevel.getBlockEntity(position);
        if (!(entity instanceof StructureBlockEntity structureBlockEntity)) {
            return Boolean.FALSE;
        }
        if (structureBlockEntity.getMode() != StructureMode.CORNER) {
            return Boolean.FALSE;
        }
        String otherName = structureBlockEntity.getStructureName();
        if (otherName == null) {
            return Boolean.FALSE;
        }
        if (!otherName.equals(structureNameKey)) {
            return Boolean.FALSE;
        }
        return Boolean.TRUE;
    }

    @Unique
    private void wildernessodysseyapi$collectFarCorners(ServerLevel serverLevel, BlockPos origin, String structureNameKey,
            java.util.List<BlockPos> cornerMarkers, java.util.Set<BlockPos> knownCorners, int scannedRadius) {
        if (structureNameKey == null) {
            return;
        }

        int searchRadius = StructureBlockSettings.getCornerSearchRadius();
        if (searchRadius <= scannedRadius) {
            return;
        }

        StructureBlockCornerCache cache = StructureBlockCornerCache.getIfPresent(serverLevel);
        boolean addedFromCache = false;
        if (cache != null) {
            java.util.List<BlockPos> cachedCorners = cache.findCorners(structureNameKey, origin, searchRadius);
            for (BlockPos cachedCorner : cachedCorners) {
                if (cachedCorner.equals(origin)) {
                    continue;
                }
                if (knownCorners.contains(cachedCorner)) {
                    continue;
                }
                java.lang.Boolean validation = wildernessodysseyapi$validateCorner(serverLevel, cachedCorner,
                        structureNameKey);
                if (java.lang.Boolean.FALSE.equals(validation)) {
                    cache.removeCorner(cachedCorner);
                    continue;
                }
                if (validation == null) {
                    continue;
                }
                knownCorners.add(cachedCorner);
                cornerMarkers.add(cachedCorner);
                addedFromCache = true;
            }
        }
        if (addedFromCache) {
            return;
        }

        ServerChunkCache chunkSource = serverLevel.getChunkSource();
        int minX = origin.getX() - searchRadius;
        int maxX = origin.getX() + searchRadius;
        int minZ = origin.getZ() - searchRadius;
        int maxZ = origin.getZ() + searchRadius;
        int minY = Math.max(serverLevel.getMinBuildHeight(), origin.getY() - searchRadius);
        int maxY = Math.min(serverLevel.getMaxBuildHeight() - 1, origin.getY() + searchRadius);

        int minChunkX = minX >> 4;
        int maxChunkX = maxX >> 4;
        int minChunkZ = minZ >> 4;
        int maxChunkZ = maxZ >> 4;

        StructureBlockCornerCache fallbackCache = StructureBlockCornerCache.get(serverLevel);

        for (int chunkX = minChunkX; chunkX <= maxChunkX; chunkX++) {
            for (int chunkZ = minChunkZ; chunkZ <= maxChunkZ; chunkZ++) {
                LevelChunk chunk = chunkSource.getChunkNow(chunkX, chunkZ);
                if (chunk == null) {
                    continue;
                }
                java.util.Collection<BlockEntity> blockEntities = chunk.getBlockEntities().values();
                if (blockEntities.isEmpty()) {
                    continue;
                }
                for (BlockEntity blockEntity : blockEntities) {
                    if (!(blockEntity instanceof StructureBlockEntity structureBlockEntity)) {
                        continue;
                    }
                    if (structureBlockEntity.getMode() != StructureMode.CORNER) {
                        continue;
                    }
                    String otherName = structureBlockEntity.getStructureName();
                    if (otherName == null || !otherName.equals(structureNameKey)) {
                        continue;
                    }
                    BlockPos pos = blockEntity.getBlockPos();
                    if (pos.equals(origin)) {
                        continue;
                    }
                    if (pos.getY() < minY || pos.getY() > maxY) {
                        continue;
                    }
                    if (pos.getX() < minX || pos.getX() > maxX || pos.getZ() < minZ || pos.getZ() > maxZ) {
                        continue;
                    }
                    BlockPos immutablePos = pos.immutable();
                    if (knownCorners.add(immutablePos)) {
                        cornerMarkers.add(immutablePos);
                    }
                    fallbackCache.addCorner(immutablePos, structureNameKey);
                }
            }
        }
    }

    @Unique
    private void wildernessodysseyapi$warmupChunks(ServerLevel serverLevel, int minX, int maxX, int minZ, int maxZ) {
        int budget = StructureBlockSettings.getChunkWarmupBudget();
        if (budget <= 0) {
            return;
        }

        ServerChunkCache chunkSource = serverLevel.getChunkSource();
        int minChunkX = minX >> 4;
        int maxChunkX = maxX >> 4;
        int minChunkZ = minZ >> 4;
        int maxChunkZ = maxZ >> 4;
        int warmed = 0;

        for (int chunkX = minChunkX; chunkX <= maxChunkX; chunkX++) {
            for (int chunkZ = minChunkZ; chunkZ <= maxChunkZ; chunkZ++) {
                if (chunkSource.getChunkNow(chunkX, chunkZ) != null) {
                    continue;
                }
                serverLevel.getChunk(chunkX, chunkZ);
                if (++warmed >= budget) {
                    return;
                }
            }
        }
    }

    @Inject(method = "saveStructure", at = @At("RETURN"))
    private void wildernessodysseyapi$recompressStructureFile(CallbackInfoReturnable<Boolean> cir) {
        if (!cir.getReturnValue()) {
            return;
        }

        Level currentLevel = this.level;
        if (!(currentLevel instanceof ServerLevel serverLevel)) {
            return;
        }
        String structureName = this.getStructureName();
        if (structureName == null || structureName.isBlank()) {
            return;
        }
        int compressionLevel = StructureBlockSettings.getStructureCompressionLevel();
        if (compressionLevel <= 0) {
            return;
        }

        ResourceLocation location = ResourceLocation.tryParse(structureName);
        if (location == null) {
            return;
        }

        java.nio.file.Path structurePath = serverLevel.getServer().getWorldPath(LevelResource.GENERATED_DIR)
                .resolve(location.getNamespace()).resolve("structures").resolve(location.getPath() + ".nbt");
        if (!Files.exists(structurePath)) {
            return;
        }

        NbtCompressionUtils.rewriteCompressed(structurePath, compressionLevel);
    }

    @Unique
    private void wildernessodysseyapi$syncCornerCache() {
        String normalizedName = wildernessodysseyapi$normalizeStructureName(this.getStructureName());
        Level currentLevel = this.level;
        if (!(currentLevel instanceof ServerLevel serverLevel)) {
            wildernessodysseyapi$removeCornerFromCache();
            return;
        }
        if (this.getMode() != StructureMode.CORNER || normalizedName == null) {
            wildernessodysseyapi$removeCornerFromCache();
            return;
        }
        if (this.wildernessodysseyapi$cacheRegistered && this.wildernessodysseyapi$cachedCornerLevel == serverLevel
                && normalizedName.equals(this.wildernessodysseyapi$cachedCornerName)) {
            return;
        }
        if (this.wildernessodysseyapi$cacheRegistered) {
            wildernessodysseyapi$removeCornerFromCache();
        }
        StructureBlockCornerCache.get(serverLevel).addCorner(this.getBlockPos(), normalizedName);
        this.wildernessodysseyapi$cachedCornerLevel = serverLevel;
        this.wildernessodysseyapi$cachedCornerName = normalizedName;
        this.wildernessodysseyapi$cacheRegistered = true;
    }

    @Unique
    private void wildernessodysseyapi$removeCornerFromCache() {
        if (!this.wildernessodysseyapi$cacheRegistered) {
            return;
        }
        ServerLevel cachedLevel = this.wildernessodysseyapi$cachedCornerLevel;
        if (cachedLevel != null) {
            StructureBlockCornerCache cache = StructureBlockCornerCache.getIfPresent(cachedLevel);
            if (cache != null) {
                cache.removeCorner(this.getBlockPos());
            }
        }
        this.wildernessodysseyapi$cacheRegistered = false;
        this.wildernessodysseyapi$cachedCornerName = null;
        this.wildernessodysseyapi$cachedCornerLevel = null;
    }

    @Override
    public void wildernessodysseyapi$bridge$syncCornerCache() {
        wildernessodysseyapi$syncCornerCache();
    }

    @Override
    public void wildernessodysseyapi$bridge$removeCornerFromCache() {
        wildernessodysseyapi$removeCornerFromCache();
    }

    @Unique
    private static @org.jetbrains.annotations.Nullable String wildernessodysseyapi$normalizeStructureName(
            @org.jetbrains.annotations.Nullable String name) {
        if (name == null || name.isEmpty()) {
            return null;
        }
        return name;
    }

    @Unique
    private void wildernessodysseyapi$placeCornerBlocks(ServerLevel serverLevel, BlockPos structureBlockPos, BlockPos minCorner, Vec3i size) {
        ResourceLocation structureName = this.structureName;
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