package com.thunder.wildernessodysseyapi.mixin;

import com.thunder.wildernessodysseyapi.watersystem.water.compat.neoforge.WaterMutationSafety;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.piston.MovingPistonBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Optional Sable 2.0.6 water bridge. Its state-only classifier calls collision
 * shapes at ZERO with a loading LevelAccelerator. During our admitted writes,
 * evaluate at the supplied position with a non-loading view instead. Sable's
 * Rapier callbacks, TreePhysics wrapper and all unrelated calls still execute.
 * See docs/watersystem/sable-water-stall.md for the exact upstream seam.
 */
@Pseudo
@Mixin(targets = "dev.ryanhcode.sable.physics.chunk.VoxelNeighborhoodState", remap = false)
public abstract class SableWaterShapeMixin {
    @Inject(method = "isSolid", at = @At("HEAD"), cancellable = true, remap = false)
    private static void wildernessOdysseyApi$waterSolid(BlockGetter original, BlockPos pos, BlockState state,
            CallbackInfoReturnable<Boolean> callback) {
        BlockGetter reader = WaterMutationSafety.shapeReader();
        if (reader != null) {
            callback.setReturnValue(!state.isAir() && (state.getBlock() instanceof MovingPistonBlock
                    || !state.getCollisionShape(reader, pos).isEmpty()));
        }
    }

    @Inject(method = "isFullBlock", at = @At("HEAD"), cancellable = true, remap = false)
    private static void wildernessOdysseyApi$waterFullBlock(BlockGetter original, BlockPos pos, BlockState state,
            CallbackInfoReturnable<Boolean> callback) {
        BlockGetter reader = WaterMutationSafety.shapeReader();
        if (reader != null) callback.setReturnValue(!state.isAir() && state.isCollisionShapeFullBlock(reader, pos));
    }
}
