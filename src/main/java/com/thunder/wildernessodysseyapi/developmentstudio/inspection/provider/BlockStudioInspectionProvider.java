package com.thunder.wildernessodysseyapi.developmentstudio.inspection.provider;

import com.thunder.wildernessodysseyapi.core.ModConstants;
import com.thunder.wildernessodysseyapi.developmentstudio.inspection.StudioBlockTarget;
import com.thunder.wildernessodysseyapi.developmentstudio.inspection.StudioInspection;
import com.thunder.wildernessodysseyapi.developmentstudio.inspection.StudioInspectionLine;
import com.thunder.wildernessodysseyapi.developmentstudio.inspection.StudioInspectionProvider;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.List;

/** Exposes registry, position, state, fluid, and block-entity facts for a real block. */
public final class BlockStudioInspectionProvider implements StudioInspectionProvider<StudioBlockTarget> {
    private static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath(
            ModConstants.MOD_ID, "block"
    );

    @Override
    public ResourceLocation id() {
        return ID;
    }

    @Override
    public Class<StudioBlockTarget> targetType() {
        return StudioBlockTarget.class;
    }

    @Override
    public StudioInspection inspect(ServerPlayer player, StudioBlockTarget target) {
        BlockState state = target.level().getBlockState(target.position());
        List<StudioInspectionLine> lines = new ArrayList<>();
        lines.add(new StudioInspectionLine("Registry ID", BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString()));
        lines.add(new StudioInspectionLine("Position", target.position().toShortString()));
        lines.add(new StudioInspectionLine("Dimension", target.level().dimension().location().toString()));
        lines.add(new StudioInspectionLine("Block state", state.toString()));
        lines.add(new StudioInspectionLine("Fluid state", state.getFluidState().isEmpty()
                ? "empty"
                : state.getFluidState().toString()));

        BlockEntity blockEntity = target.level().getBlockEntity(target.position());
        if (blockEntity == null) {
            lines.add(new StudioInspectionLine("Block entity", "none"));
        } else {
            lines.add(new StudioInspectionLine(
                    "Block entity type",
                    BuiltInRegistries.BLOCK_ENTITY_TYPE.getKey(blockEntity.getType()).toString()
            ));
            lines.add(new StudioInspectionLine("Block entity removed", Boolean.toString(blockEntity.isRemoved())));
        }
        return new StudioInspection(ID, "Block Inspector", lines);
    }
}
