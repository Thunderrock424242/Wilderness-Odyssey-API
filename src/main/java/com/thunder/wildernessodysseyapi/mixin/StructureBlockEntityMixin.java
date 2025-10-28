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
}
