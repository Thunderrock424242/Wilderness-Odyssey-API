package com.thunder.wildernessodysseyapi.mixin;

import com.thunder.wildernessodysseyapi.util.StructureBlockSettings;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.entity.StructureBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
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

    @Shadow public abstract void setStructurePos(BlockPos pos);
    @Shadow public abstract void setStructureSize(Vec3i size);
    @Shadow public abstract StructureMode getMode();

    protected StructureBlockEntityMixin(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    @Unique
    private static boolean wildernessodysseyapi$limitsExpanded;

    @Inject(method = "<init>", at = @At("TAIL"))
    private void wildernessodysseyapi$expandLimits(BlockPos pos, BlockState state, CallbackInfo ci) {
        if (wildernessodysseyapi$limitsExpanded) {
            return;
        }
        wildernessodysseyapi$limitsExpanded = true;
        MAX_SIZE_PER_AXIS = StructureBlockSettings.MAX_STRUCTURE_SIZE;
        MAX_OFFSET_PER_AXIS = StructureBlockSettings.MAX_STRUCTURE_OFFSET;
    }

    @Inject(method = "saveStructure", at = @At("HEAD"))
    private void wildernessodysseyapi$autoFitStructure(CallbackInfoReturnable<Boolean> cir) {
        Level level = this.level;
        if (!(level instanceof ServerLevel serverLevel)) {
            return;
        }
        this.wildernessodysseyapi$fitStructureToContent(serverLevel, false);
    }

    @Inject(method = "detectSize", at = @At("HEAD"), cancellable = true)
    private void wildernessodysseyapi$improvedDetect(CallbackInfoReturnable<Boolean> cir) {
        Level level = this.level;
        if (!(level instanceof ServerLevel serverLevel)) {
            return;
        }
        if (this.wildernessodysseyapi$fitStructureToContent(serverLevel, true)) {
            cir.setReturnValue(true);
            cir.cancel();
        }
    }

    @Unique
    private boolean wildernessodysseyapi$fitStructureToContent(ServerLevel serverLevel, boolean allowFallback) {
        if (this.getMode() != StructureMode.SAVE) {
            return false;
        }

        BlockPos blockPos = this.getBlockPos();

        boolean hasDefinedSize = this.structureSize.getX() > 0 && this.structureSize.getY() > 0 && this.structureSize.getZ() > 0;
        BlockPos start;
        BlockPos end;

        if (hasDefinedSize) {
            start = blockPos.offset(this.structurePos);
            end = start.offset(this.structureSize.getX() - 1, this.structureSize.getY() - 1, this.structureSize.getZ() - 1);
        } else {
            if (!allowFallback) {
                return false;
            }
            int radius = StructureBlockSettings.DEFAULT_DETECTION_RADIUS;
            start = blockPos.offset(-radius, -radius, -radius);
            end = blockPos.offset(radius, radius, radius);
        }

        int minX = Math.min(start.getX(), end.getX());
        int minY = Math.min(start.getY(), end.getY());
        int minZ = Math.min(start.getZ(), end.getZ());
        int maxX = Math.max(start.getX(), end.getX());
        int maxY = Math.max(start.getY(), end.getY());
        int maxZ = Math.max(start.getZ(), end.getZ());

        int levelMinY = serverLevel.getMinBuildHeight();
        int levelMaxY = serverLevel.getMaxBuildHeight() - 1;

        minY = Math.max(minY, levelMinY);
        maxY = Math.min(maxY, levelMaxY);

        if (minY > maxY) {
            return false;
        }

        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        boolean found = false;
        int detectedMinX = Integer.MAX_VALUE;
        int detectedMinY = Integer.MAX_VALUE;
        int detectedMinZ = Integer.MAX_VALUE;
        int detectedMaxX = Integer.MIN_VALUE;
        int detectedMaxY = Integer.MIN_VALUE;
        int detectedMaxZ = Integer.MIN_VALUE;

        for (int x = minX; x <= maxX; x++) {
            for (int y = minY; y <= maxY; y++) {
                for (int z = minZ; z <= maxZ; z++) {
                    cursor.set(x, y, z);
                    if (!StructureBlockSettings.isStructureContent(serverLevel.getBlockState(cursor))) {
                        continue;
                    }
                    if (cursor.equals(blockPos)) {
                        continue;
                    }
                    found = true;
                    if (x < detectedMinX) {
                        detectedMinX = x;
                    }
                    if (y < detectedMinY) {
                        detectedMinY = y;
                    }
                    if (z < detectedMinZ) {
                        detectedMinZ = z;
                    }
                    if (x > detectedMaxX) {
                        detectedMaxX = x;
                    }
                    if (y > detectedMaxY) {
                        detectedMaxY = y;
                    }
                    if (z > detectedMaxZ) {
                        detectedMaxZ = z;
                    }
                }
            }
        }

        if (!found) {
            return false;
        }

        BlockPos newStart = new BlockPos(detectedMinX, detectedMinY, detectedMinZ);
        BlockPos newSize = new BlockPos(detectedMaxX - detectedMinX + 1, detectedMaxY - detectedMinY + 1, detectedMaxZ - detectedMinZ + 1);
        BlockPos relativePos = newStart.subtract(blockPos);

        if (!relativePos.equals(this.structurePos)) {
            this.setStructurePos(relativePos);
        }
        if (!newSize.equals(this.structureSize)) {
            this.setStructureSize(newSize);
        }

        return true;
    }
}
