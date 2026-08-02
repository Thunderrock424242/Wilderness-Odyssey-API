package com.thunder.wildernessodysseyapi.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.thunder.wildernessodysseyapi.watersystem.water.fluid.WildernessFluidRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BucketItem;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.LiquidBlockContainer;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

import javax.annotation.Nullable;

/**
 * Lets the Wilderness water bucket use vanilla waterlogging contracts.
 *
 * <p>{@link LiquidBlockContainer} implementations such as
 * {@code SimpleWaterloggedBlock} compare fluid identity directly with
 * {@link Fluids#WATER}. A mixin is required because neither BucketItem nor the
 * container interface exposes a tag-aware compatibility hook. Only the fluid
 * arguments presented to a container are translated; ordinary block placement
 * still uses the registered Wilderness fluid and remains canonical.</p>
 */
@Mixin(BucketItem.class)
public abstract class BucketItemWaterloggingMixin {

    /**
     * Makes the initial targeted-block probe recognize Wilderness water so a
     * fence, slab, or other container is selected instead of the adjacent cell.
     */
    @WrapOperation(
            method = "canBlockContainFluid",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/level/block/LiquidBlockContainer;"
                            + "canPlaceLiquid(Lnet/minecraft/world/entity/player/Player;"
                            + "Lnet/minecraft/world/level/BlockGetter;Lnet/minecraft/core/BlockPos;"
                            + "Lnet/minecraft/world/level/block/state/BlockState;"
                            + "Lnet/minecraft/world/level/material/Fluid;)Z"
            ),
            require = 1
    )
    private boolean wildernessOdysseyApi$acceptWildernessWaterDuringTargetProbe(
            LiquidBlockContainer container,
            @Nullable Player player,
            BlockGetter level,
            BlockPos pos,
            BlockState state,
            Fluid offeredFluid,
            Operation<Boolean> original
    ) {
        return original.call(
                container,
                player,
                level,
                pos,
                state,
                wildernessOdysseyApi$containerFluid(offeredFluid)
        );
    }

    /**
     * Preserves both of BucketItem's placement checks while presenting
     * Wilderness water as vanilla water only to the selected container.
     */
    @WrapOperation(
            method = "emptyContents(Lnet/minecraft/world/entity/player/Player;"
                    + "Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;"
                    + "Lnet/minecraft/world/phys/BlockHitResult;"
                    + "Lnet/minecraft/world/item/ItemStack;)Z",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/level/block/LiquidBlockContainer;"
                            + "canPlaceLiquid(Lnet/minecraft/world/entity/player/Player;"
                            + "Lnet/minecraft/world/level/BlockGetter;Lnet/minecraft/core/BlockPos;"
                            + "Lnet/minecraft/world/level/block/state/BlockState;"
                            + "Lnet/minecraft/world/level/material/Fluid;)Z"
            ),
            require = 2
    )
    private boolean wildernessOdysseyApi$acceptWildernessWaterDuringPlacement(
            LiquidBlockContainer container,
            @Nullable Player player,
            BlockGetter level,
            BlockPos pos,
            BlockState state,
            Fluid offeredFluid,
            Operation<Boolean> original
    ) {
        return original.call(
                container,
                player,
                level,
                pos,
                state,
                wildernessOdysseyApi$containerFluid(offeredFluid)
        );
    }

    /**
     * Stores vanilla water inside the host block, matching the representation
     * that vanilla waterlogged blocks can later tick and return to a bucket.
     */
    @WrapOperation(
            method = "emptyContents(Lnet/minecraft/world/entity/player/Player;"
                    + "Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;"
                    + "Lnet/minecraft/world/phys/BlockHitResult;"
                    + "Lnet/minecraft/world/item/ItemStack;)Z",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/level/block/LiquidBlockContainer;"
                            + "placeLiquid(Lnet/minecraft/world/level/LevelAccessor;"
                            + "Lnet/minecraft/core/BlockPos;"
                            + "Lnet/minecraft/world/level/block/state/BlockState;"
                            + "Lnet/minecraft/world/level/material/FluidState;)Z"
            ),
            require = 1
    )
    private boolean wildernessOdysseyApi$storeVanillaWaterInHost(
            LiquidBlockContainer container,
            LevelAccessor level,
            BlockPos pos,
            BlockState state,
            FluidState offeredState,
            Operation<Boolean> original
    ) {
        FluidState containerState = wildernessOdysseyApi$isWildernessWater(offeredState.getType())
                ? Fluids.WATER.getSource(false)
                : offeredState;
        return original.call(container, level, pos, state, containerState);
    }

    @Unique
    private static Fluid wildernessOdysseyApi$containerFluid(Fluid offeredFluid) {
        return wildernessOdysseyApi$isWildernessWater(offeredFluid) ? Fluids.WATER : offeredFluid;
    }

    @Unique
    private static boolean wildernessOdysseyApi$isWildernessWater(Fluid fluid) {
        return fluid.isSame(WildernessFluidRegistry.WILDERNESS_WATER.get())
                || fluid.isSame(WildernessFluidRegistry.FLOWING_WILDERNESS_WATER.get());
    }
}
