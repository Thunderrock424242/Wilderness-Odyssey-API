package com.thunder.wildernessodysseyapi.mixin;

import com.thunder.wildernessodysseyapi.watersystem.water.compat.neoforge.WorldFluidMutationReconciler;
import com.thunder.wildernessodysseyapi.watersystem.water.volume.CanonicalWater;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Marks physical block writes that originate from canonical water authority.
 *
 * <p>The global projection-mutation boundary cannot otherwise distinguish a
 * machine's direct block edit from {@link CanonicalWater} rendering its own
 * fixed-point state. Redirecting only the two writes in
 * {@code projectCompatibility} prevents internal physics and machine-handler
 * commits from being interpreted as a second external transfer.</p>
 */
@Mixin(CanonicalWater.class)
public abstract class CanonicalWaterProjectionGuardMixin {

    /**
     * Runs canonical projection under the reconciler's narrow recursion guard.
     */
    @Redirect(
            method = "projectCompatibility",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/server/level/ServerLevel;setBlock("
                            + "Lnet/minecraft/core/BlockPos;"
                            + "Lnet/minecraft/world/level/block/state/BlockState;I)Z"
            ),
            require = 2
    )
    private static boolean wildernessOdysseyApi$guardCanonicalProjection(
            ServerLevel level,
            BlockPos position,
            BlockState state,
            int flags
    ) {
        return WorldFluidMutationReconciler.setCanonicalProjectionBlock(
                level,
                position,
                state,
                flags
        );
    }
}
