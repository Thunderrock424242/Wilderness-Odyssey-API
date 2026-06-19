package com.thunder.wildernessodysseyapi.mixin;

import com.thunder.wildernessodysseyapi.structureblock.bridge.StructureBlockCornerCacheBridge;
import com.thunder.wildernessodysseyapi.core.ModConstants;
import com.thunder.wildernessodysseyapi.structureblock.StructureBlockCornerCache;
import com.thunder.wildernessodysseyapi.structureblock.StructureBlockDetectionContext;
import com.thunder.wildernessodysseyapi.util.NbtCompressionUtils;
import com.thunder.wildernessodysseyapi.structureblock.StructureBlockHostileSpawnContext;
import com.thunder.wildernessodysseyapi.structureblock.StructureBlockSettings;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.Vec3i;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerChunkCache;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.entity.StructureBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.properties.StructureMode;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.util.Mth;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.Tag;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
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
                java.util.List<BlockPos> cachedCorners = cache.findCornersUnsorted(structureNameKey, blockPos,
                        detectionRadius);
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
            wildernessodysseyapi$scanCornersInCube(serverLevel, blockPos, structureNameKey, minXBound, maxXBound,
                    minYBound, maxYBound, minZBound, maxZBound, cornerMarkers, knownCorners);
        }

        wildernessodysseyapi$collectFarCorners(serverLevel, blockPos, structureNameKey, cornerMarkers, knownCorners,
                detectionRadius);

        if (structureNameKey != null && cornerMarkers.size() < 2) {
            wildernessodysseyapi$reportCornerScanDiagnostics(serverLevel, blockPos, structureNameKey, detectionRadius,
                    cornerMarkers.size());
        }

        if (!cornerMarkers.isEmpty()) {
            boolean hasBounds = false;
            int minX = structureX;
            int minY = structureY;
            int minZ = structureZ;
            int maxX = structureX;
            int maxY = structureY;
            int maxZ = structureZ;

            for (BlockPos corner : cornerMarkers) {
                if (corner.equals(blockPos)) {
                    continue;
                }
                hasBounds = true;
                int x = corner.getX();
                int y = corner.getY();
                int z = corner.getZ();
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

            if (hasBounds) {
                BlockPos newStart = new BlockPos(minX, minY, minZ);
                BlockPos newSize = new BlockPos(maxX - minX + 1, maxY - minY + 1, maxZ - minZ + 1);
                wildernessodysseyapi$reportCornerDetection(cornerMarkers.size(), blockPos, newStart, newSize);
                wildernessodysseyapi$applyDetectedBounds(serverLevel, blockPos, newStart, newSize, cir);
                return;
            }
        }

        StructureBlockDetectionContext.send("WO Detect: found no matching CORNER blocks; using content scan.");

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
            if (!cornerBelowX && cornerAboveX) {
                minX = structureX;
            } else if (!cornerAboveX && cornerBelowX) {
                maxX = structureX;
            }
            if (!cornerBelowY && cornerAboveY) {
                minY = structureY;
            } else if (!cornerAboveY && cornerBelowY) {
                maxY = structureY;
            }
            if (!cornerBelowZ && cornerAboveZ) {
                minZ = structureZ;
            } else if (!cornerAboveZ && cornerBelowZ) {
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
        wildernessodysseyapi$applyDetectedBounds(serverLevel, blockPos, newStart, newSize, cir);
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
        String structureNameKey = wildernessodysseyapi$normalizeStructureName(this.getStructureName());
        if (structureNameKey != null
                && wildernessodysseyapi$hasManualCornerBounds(serverLevel, blockPos, structureNameKey, start, end)) {
            return;
        }

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
    private void wildernessodysseyapi$applyDetectedBounds(ServerLevel serverLevel, BlockPos blockPos, BlockPos newStart,
            BlockPos newSize, CallbackInfoReturnable<Boolean> cir) {
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

        cir.setReturnValue(true);
        cir.cancel();
    }

    @Unique
    private void wildernessodysseyapi$reportCornerDetection(int cornerCount, BlockPos blockPos, BlockPos newStart,
            BlockPos newSize) {
        BlockPos relativePos = newStart.subtract(blockPos);
        StructureBlockDetectionContext.send("WO Detect: found " + cornerCount + " CORNER marker(s); offset="
                + wildernessodysseyapi$formatVec(relativePos) + " size=" + wildernessodysseyapi$formatVec(newSize));
        if (cornerCount < 2 || newSize.getX() <= 1 || newSize.getY() <= 1 || newSize.getZ() <= 1) {
            StructureBlockDetectionContext.send(
                    "WO Detect: one axis is still 1 block; add/name a CORNER marker on the missing width/height/depth.");
        }
    }

    @Unique
    private static String wildernessodysseyapi$formatVec(Vec3i vec) {
        return vec.getX() + "," + vec.getY() + "," + vec.getZ();
    }

    @Unique
    private void wildernessodysseyapi$reportCornerScanDiagnostics(ServerLevel serverLevel, BlockPos origin,
            String structureNameKey, int detectionRadius, int acceptedCount) {
        int searchRadius = Math.max(detectionRadius, StructureBlockSettings.getCornerSearchRadius());
        int minX = origin.getX() - searchRadius;
        int maxX = origin.getX() + searchRadius;
        int minZ = origin.getZ() - searchRadius;
        int maxZ = origin.getZ() + searchRadius;
        int minY = Math.max(serverLevel.getMinBuildHeight(), origin.getY() - searchRadius);
        int maxY = Math.min(serverLevel.getMaxBuildHeight() - 1, origin.getY() + searchRadius);

        ServerChunkCache chunkSource = serverLevel.getChunkSource();
        int minChunkX = minX >> 4;
        int maxChunkX = maxX >> 4;
        int minChunkZ = minZ >> 4;
        int maxChunkZ = maxZ >> 4;

        int unloadedChunks = 0;
        int structureBlocks = 0;
        int cornerMode = 0;
        int matchingName = 0;
        int blankName = 0;
        int wrongName = 0;
        int otherMode = 0;
        java.util.LinkedHashSet<String> wrongNames = new java.util.LinkedHashSet<>();

        for (int chunkX = minChunkX; chunkX <= maxChunkX; chunkX++) {
            for (int chunkZ = minChunkZ; chunkZ <= maxChunkZ; chunkZ++) {
                LevelChunk chunk = chunkSource.getChunkNow(chunkX, chunkZ);
                if (chunk == null) {
                    unloadedChunks++;
                    continue;
                }
                for (BlockEntity blockEntity : chunk.getBlockEntities().values()) {
                    if (!(blockEntity instanceof StructureBlockEntity structureBlockEntity)) {
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

                    structureBlocks++;
                    if (structureBlockEntity.getMode() != StructureMode.CORNER) {
                        otherMode++;
                        continue;
                    }

                    cornerMode++;
                    String otherName = wildernessodysseyapi$normalizeStructureName(structureBlockEntity.getStructureName());
                    if (structureNameKey.equals(otherName)) {
                        matchingName++;
                    } else if (otherName == null) {
                        blankName++;
                    } else {
                        wrongName++;
                        if (wrongNames.size() < 3) {
                            wrongNames.add(otherName);
                        }
                    }
                }
            }
        }

        StructureBlockDetectionContext.send("WO Detect debug: accepted=" + acceptedCount + " radius=" + searchRadius
                + " structureBlocks=" + structureBlocks + " cornerMode=" + cornerMode + " unloadedChunks="
                + unloadedChunks);
        StructureBlockDetectionContext.send("WO Detect debug: matchingName=" + matchingName + " blankName="
                + blankName + " wrongName=" + wrongName + " otherMode=" + otherMode);
        if (!wrongNames.isEmpty()) {
            StructureBlockDetectionContext.send("WO Detect debug: sample wrong names=" + String.join(", ", wrongNames));
        }
    }

    @Unique
    private void wildernessodysseyapi$scanCornersInCube(ServerLevel serverLevel, BlockPos origin, String structureNameKey,
            int minX, int maxX, int minY, int maxY, int minZ, int maxZ, java.util.List<BlockPos> cornerMarkers,
            java.util.Set<BlockPos> knownCorners) {
        wildernessodysseyapi$collectLoadedCornersInBox(serverLevel, origin, structureNameKey, minX, maxX, minY, maxY,
                minZ, maxZ, cornerMarkers, knownCorners, true);
    }

    @Unique
    private java.lang.Boolean wildernessodysseyapi$validateCorner(ServerLevel serverLevel, BlockPos position,
            String structureNameKey) {
        return wildernessodysseyapi$validateCorner(serverLevel, position, structureNameKey, false);
    }

    @Unique
    private java.lang.Boolean wildernessodysseyapi$validateCorner(ServerLevel serverLevel, BlockPos position,
            String structureNameKey, boolean allowUnnamedCorner) {
        ChunkPos chunkPos = new ChunkPos(position);
        if (!serverLevel.hasChunk(chunkPos.x, chunkPos.z)) {
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
        if (!wildernessodysseyapi$cornerNameMatches(structureBlockEntity, structureNameKey, allowUnnamedCorner)) {
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
        if (cache != null) {
            java.util.List<BlockPos> cachedCorners = cache.findCornersUnsorted(structureNameKey, origin, searchRadius);
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
            }
        }

        int minX = origin.getX() - searchRadius;
        int maxX = origin.getX() + searchRadius;
        int minZ = origin.getZ() - searchRadius;
        int maxZ = origin.getZ() + searchRadius;
        int minY = Math.max(serverLevel.getMinBuildHeight(), origin.getY() - searchRadius);
        int maxY = Math.min(serverLevel.getMaxBuildHeight() - 1, origin.getY() + searchRadius);

        wildernessodysseyapi$collectLoadedCornersInBox(serverLevel, origin, structureNameKey, minX, maxX, minY, maxY,
                minZ, maxZ, cornerMarkers, knownCorners, true);
    }

    @Unique
    private void wildernessodysseyapi$collectLoadedCornersInBox(ServerLevel serverLevel, BlockPos origin,
            String structureNameKey, int minX, int maxX, int minY, int maxY, int minZ, int maxZ,
            java.util.List<BlockPos> cornerMarkers, java.util.Set<BlockPos> knownCorners, boolean allowUnnamedCorners) {
        ServerChunkCache chunkSource = serverLevel.getChunkSource();
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
                    if (!wildernessodysseyapi$cornerNameMatches(structureBlockEntity, structureNameKey,
                            allowUnnamedCorners)) {
                        continue;
                    }
                    BlockPos immutablePos = pos.immutable();
                    if (knownCorners.add(immutablePos)) {
                        cornerMarkers.add(immutablePos);
                    }
                    if (wildernessodysseyapi$cornerNameMatches(structureBlockEntity, structureNameKey, false)) {
                        fallbackCache.addCorner(immutablePos, structureNameKey);
                    }
                }
            }
        }
    }

    @Unique
    private boolean wildernessodysseyapi$hasManualCornerBounds(ServerLevel serverLevel, BlockPos origin,
            String structureNameKey, BlockPos start, BlockPos end) {
        int minX = Math.min(start.getX(), end.getX());
        int maxX = Math.max(start.getX(), end.getX());
        int minY = Math.min(start.getY(), end.getY());
        int maxY = Math.max(start.getY(), end.getY());
        int minZ = Math.min(start.getZ(), end.getZ());
        int maxZ = Math.max(start.getZ(), end.getZ());
        java.util.List<BlockPos> cornerMarkers = new java.util.ArrayList<>();
        java.util.Set<BlockPos> knownCorners = new java.util.HashSet<>();
        wildernessodysseyapi$collectLoadedCornersInBox(serverLevel, origin, structureNameKey, minX, maxX, minY, maxY,
                minZ, maxZ, cornerMarkers, knownCorners, true);
        return !cornerMarkers.isEmpty();
    }

    @Unique
    private boolean wildernessodysseyapi$cornerNameMatches(StructureBlockEntity structureBlockEntity,
            String structureNameKey, boolean allowUnnamedCorner) {
        String otherName = wildernessodysseyapi$normalizeStructureName(structureBlockEntity.getStructureName());
        return structureNameKey.equals(otherName) || allowUnnamedCorner && otherName == null;
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
        boolean disableHostileSpawns = StructureBlockHostileSpawnContext.isDisableHostileSpawns();
        int compressionLevel = StructureBlockSettings.getStructureCompressionLevel();
        if (!disableHostileSpawns && compressionLevel <= 0) {
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

        if (disableHostileSpawns) {
            wildernessodysseyapi$stripHostileEntities(structurePath);
        }
        if (compressionLevel > 0) {
            NbtCompressionUtils.rewriteCompressedAsync(structurePath, compressionLevel, com.thunder.wildernessodysseyapi.io.CompressionCodec.VANILLA_GZIP);
        }
    }

    @Unique
    private static void wildernessodysseyapi$stripHostileEntities(java.nio.file.Path structurePath) {
        try {
            CompoundTag root = NbtIo.readCompressed(structurePath, NbtAccounter.unlimitedHeap());
            if (root == null || !root.contains("entities", Tag.TAG_LIST)) {
                return;
            }
            ListTag sourceEntities = root.getList("entities", Tag.TAG_COMPOUND);
            if (sourceEntities.isEmpty()) {
                return;
            }

            ListTag filteredEntities = new ListTag();
            for (int i = 0; i < sourceEntities.size(); i++) {
                CompoundTag entityEntry = sourceEntities.getCompound(i);
                CompoundTag entityNbt = entityEntry.getCompound("nbt");
                String id = entityNbt.getString("id");
                if (id.isBlank()) {
                    filteredEntities.add(entityEntry.copy());
                    continue;
                }
                EntityType<?> entityType = BuiltInRegistries.ENTITY_TYPE.getOptional(ResourceLocation.tryParse(id)).orElse(null);
                if (entityType != null && entityType.getCategory() == MobCategory.MONSTER) {
                    continue;
                }
                filteredEntities.add(entityEntry.copy());
            }

            root.put("entities", filteredEntities);
            NbtIo.writeCompressed(root, structurePath);
        } catch (Exception exception) {
            ModConstants.LOGGER.warn("Failed stripping hostile entities from saved structure {}", structurePath, exception);
        }
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
        if (name == null || name.isBlank()) {
            return null;
        }
        ResourceLocation location = ResourceLocation.tryParse(name);
        return location == null ? name : location.toString();
    }

}
