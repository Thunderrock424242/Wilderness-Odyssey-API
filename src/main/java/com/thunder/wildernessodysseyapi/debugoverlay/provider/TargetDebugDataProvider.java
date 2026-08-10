package com.thunder.wildernessodysseyapi.debugoverlay.provider;

import com.thunder.wildernessodysseyapi.debugoverlay.DebugContext;
import com.thunder.wildernessodysseyapi.debugoverlay.DebugSection;
import com.thunder.wildernessodysseyapi.debugoverlay.DebugValue;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Uses vanilla's existing debug ray hits to describe the targeted block, fluid, and entity. */
public final class TargetDebugDataProvider implements DebugDataProvider {
    @Override
    public List<DebugSection> collect(DebugContext context) {
        Minecraft minecraft = context.minecraft();
        if (minecraft.level == null) {
            return List.of(DebugSection.builder("TARGET")
                    .add("State", DebugValue.unavailable("No world loaded"))
                    .build());
        }
        if (minecraft.showOnlyReducedInfo()) {
            return List.of(DebugSection.builder("TARGET")
                    .add("Details", DebugValue.unavailable("Hidden by reduced debug info"))
                    .build());
        }

        List<DebugSection> sections = new ArrayList<>();
        sections.add(blockSection(minecraft));
        sections.add(fluidSection(minecraft));
        sections.add(entitySection(minecraft));
        return List.copyOf(sections);
    }

    /** Returns registry IDs only for the General page's target summary. */
    public List<DebugSection> summary(DebugContext context) {
        Minecraft minecraft = context.minecraft();
        DebugSection.Builder target = DebugSection.builder("TARGET");
        if (minecraft.level == null || minecraft.showOnlyReducedInfo()) {
            return List.of(target
                    .add("Details", DebugValue.unavailable(minecraft.level == null ? "No world loaded" : "Reduced debug info"))
                    .build());
        }

        HitResult blockHit = DebugProviderSupport.blockTarget(minecraft);
        if (blockHit != null && blockHit.getType() == HitResult.Type.BLOCK) {
            BlockPos pos = ((BlockHitResult) blockHit).getBlockPos();
            target.add("Block", BuiltInRegistries.BLOCK.getKey(minecraft.level.getBlockState(pos).getBlock()));
        } else {
            target.add("Block", DebugValue.unavailable("—"));
        }

        HitResult fluidHit = DebugProviderSupport.fluidTarget(minecraft);
        if (fluidHit != null && fluidHit.getType() == HitResult.Type.BLOCK) {
            BlockPos pos = ((BlockHitResult) fluidHit).getBlockPos();
            FluidState state = minecraft.level.getFluidState(pos);
            target.add("Fluid", state.isEmpty()
                    ? DebugValue.unavailable("—")
                    : DebugValue.normal(BuiltInRegistries.FLUID.getKey(state.getType())));
        } else {
            target.add("Fluid", DebugValue.unavailable("—"));
        }

        Entity entity = minecraft.crosshairPickEntity;
        target.add("Entity", entity == null
                ? DebugValue.unavailable("—")
                : DebugValue.normal(BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType())));
        return List.of(target.build());
    }

    private static DebugSection blockSection(Minecraft minecraft) {
        HitResult hit = DebugProviderSupport.blockTarget(minecraft);
        if (hit == null || hit.getType() != HitResult.Type.BLOCK) {
            return DebugSection.builder("BLOCK")
                    .add("Target", DebugValue.unavailable("None"))
                    .build();
        }

        BlockPos pos = ((BlockHitResult) hit).getBlockPos();
        BlockState state = minecraft.level.getBlockState(pos);
        DebugSection.Builder section = DebugSection.builder("BLOCK")
                .add("ID", BuiltInRegistries.BLOCK.getKey(state.getBlock()))
                .add("Position", DebugProviderSupport.blockPosition(pos))
                .add("Light", minecraft.level.getChunkSource().getLightEngine().getRawBrightness(pos, 0)
                        + " (sky " + minecraft.level.getBrightness(LightLayer.SKY, pos)
                        + ", block " + minecraft.level.getBrightness(LightLayer.BLOCK, pos) + ")");
        for (Map.Entry<Property<?>, Comparable<?>> property : state.getValues().entrySet()) {
            section.add("State " + property.getKey().getName(), Util.getPropertyName(property.getKey(), property.getValue()));
        }
        state.getTags().map(tag -> "#" + tag.location()).sorted()
                .forEach(tag -> section.add("Tag", tag));

        FluidState containedFluid = state.getFluidState();
        section.add("Fluid state", containedFluid.isEmpty()
                ? DebugValue.unavailable("Empty")
                : DebugValue.normal(BuiltInRegistries.FLUID.getKey(containedFluid.getType())));
        return section.build();
    }

    private static DebugSection fluidSection(Minecraft minecraft) {
        HitResult hit = DebugProviderSupport.fluidTarget(minecraft);
        if (hit == null || hit.getType() != HitResult.Type.BLOCK) {
            return DebugSection.builder("FLUID")
                    .add("Target", DebugValue.unavailable("None"))
                    .build();
        }

        BlockPos pos = ((BlockHitResult) hit).getBlockPos();
        FluidState state = minecraft.level.getFluidState(pos);
        if (state.isEmpty()) {
            return DebugSection.builder("FLUID")
                    .add("Target", DebugValue.unavailable("None"))
                    .build();
        }

        DebugSection.Builder section = DebugSection.builder("FLUID")
                .add("ID", BuiltInRegistries.FLUID.getKey(state.getType()))
                .add("Position", DebugProviderSupport.blockPosition(pos))
                .add("Height", String.format(Locale.ROOT, "%.3f", state.getHeight(minecraft.level, pos)));
        for (Map.Entry<Property<?>, Comparable<?>> property : state.getValues().entrySet()) {
            section.add("State " + property.getKey().getName(), Util.getPropertyName(property.getKey(), property.getValue()));
        }
        state.getTags().map(tag -> "#" + tag.location()).sorted()
                .forEach(tag -> section.add("Tag", tag));
        return section.build();
    }

    private static DebugSection entitySection(Minecraft minecraft) {
        Entity entity = minecraft.crosshairPickEntity;
        if (entity == null) {
            return DebugSection.builder("ENTITY")
                    .add("Target", DebugValue.unavailable("None"))
                    .build();
        }

        DebugSection.Builder section = DebugSection.builder("ENTITY")
                .add("ID", BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()))
                .add("UUID", entity.getStringUUID())
                .add("Runtime ID", entity.getId())
                .add("Position", DebugProviderSupport.precisePosition(entity))
                .add("Name", entity.getDisplayName().getString());
        entity.getType().getTags()
                .map(tag -> "#" + tag.location()).sorted()
                .forEach(tag -> section.add("Tag", tag));
        return section.build();
    }
}
