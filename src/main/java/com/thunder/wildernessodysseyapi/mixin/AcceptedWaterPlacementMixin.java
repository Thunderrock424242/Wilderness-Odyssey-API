package com.thunder.wildernessodysseyapi.mixin;

import com.thunder.wildernessodysseyapi.watersystem.water.fluid.WildernessFluidRegistry;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.Entity;
import net.neoforged.neoforge.common.util.BlockSnapshot;
import net.neoforged.neoforge.event.EventHooks;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import java.util.List;

/**
 * Commits displaced water only after NeoForge's final placement cancellation result.
 * Even LOWEST event listeners run before later LOWEST listeners can cancel; the
 * supported event therefore cannot safely commit a persistent water transfer.
 * These narrow RETURN hooks cover accepted single and multi-block placements.
 */
@Mixin(value = EventHooks.class, remap = false)
public abstract class AcceptedWaterPlacementMixin {
    @Inject(method = "onBlockPlace", at = @At("RETURN"))
    private static void wildernessOdysseyApi$acceptedSingle(Entity entity, BlockSnapshot snapshot,
            Direction direction, CallbackInfoReturnable<Boolean> callback) {
        if (!callback.getReturnValue()) WildernessFluidRegistry.onAcceptedPlacement(List.of(snapshot));
    }

    @Inject(method = "onMultiBlockPlace", at = @At("RETURN"))
    private static void wildernessOdysseyApi$acceptedMultiple(Entity entity, List<BlockSnapshot> snapshots,
            Direction direction, CallbackInfoReturnable<Boolean> callback) {
        if (!callback.getReturnValue()) WildernessFluidRegistry.onAcceptedPlacement(snapshots);
    }
}
