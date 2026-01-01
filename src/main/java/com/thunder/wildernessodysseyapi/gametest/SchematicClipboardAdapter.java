package com.thunder.wildernessodysseyapi.gametest;

import com.sk89q.worldedit.extent.clipboard.Clipboard;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldedit.regions.Region;
import com.sk89q.worldedit.world.block.BaseBlock;
import com.sk89q.worldedit.world.block.BlockStateHolder;
import com.thunder.wildernessodysseyapi.Core.ModConstants;
import com.thunder.wildernessodysseyapi.mixin.StructureTemplateAccessor;
import net.minecraft.core.Vec3i;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Converts a WorldEdit clipboard into an in-memory {@link StructureTemplate} without relying on .nbt files.
 */
public final class SchematicClipboardAdapter {
    private SchematicClipboardAdapter() {
    }

    public static StructureTemplate toTemplate(Clipboard clipboard) {
        StructureTemplate template = new StructureTemplate();
        Region region = clipboard.getRegion();
        BlockVector3 min = region.getMinimumPoint();
        BlockVector3 size = clipboard.getDimensions();

        List<StructureTemplate.StructureBlockInfo> blocks = new ArrayList<>();
        Map<BlockState, Integer> paletteIndex = new HashMap<>();
        List<BlockState> paletteStates = new ArrayList<>();

        for (int x = 0; x < size.getX(); x++) {
            for (int y = 0; y < size.getY(); y++) {
                for (int z = 0; z < size.getZ(); z++) {
                    BlockVector3 cursor = min.add(x, y, z);
                    BaseBlock baseBlock = clipboard.getFullBlock(cursor);
                    BlockState state = convertState(baseBlock);
                    if (state == null) {
                        continue;
                    }

                    paletteIndex.computeIfAbsent(state, key -> {
                        paletteStates.add(key);
                        return paletteStates.size() - 1;
                    });

                    blocks.add(new StructureTemplate.StructureBlockInfo(
                            new net.minecraft.core.BlockPos(x, y, z),
                            state,
                            null));
                }
            }
        }

        StructureTemplate.Palette palette = createPalette(blocks);
        getPalettes(template).add(palette);
        setSize(template, new Vec3i(size.getX(), size.getY(), size.getZ()));

        return template;
    }

    private static BlockState convertState(BlockStateHolder<?> holder) {
        if (isMinecraftBootstrapMissing()) {
            return null;
        }

        ResourceLocation blockId = ResourceLocation.tryParse(holder.getBlockType().getId());
        if (blockId == null || !net.minecraft.core.registries.BuiltInRegistries.BLOCK.containsKey(blockId)) {
            return null;
        }

        BlockState state = net.minecraft.core.registries.BuiltInRegistries.BLOCK.get(blockId).defaultBlockState();
        for (Map.Entry<com.sk89q.worldedit.registry.state.Property<?>, Object> entry : holder.getStates().entrySet()) {
            Property<?> property = state.getBlock().getStateDefinition().getProperty(entry.getKey().getName());
            if (property == null) {
                continue;
            }

            Optional<?> parsed = property.getValue(String.valueOf(entry.getValue()));
            if (parsed.isPresent()) {
                state = applyProperty(state, property, parsed.get());
            }
        }

        return state;
    }

    private static <T extends Comparable<T>> BlockState applyProperty(BlockState state, Property<T> property, Object value) {
        try {
            @SuppressWarnings("unchecked")
            T cast = (T) value;
            return state.setValue(property, cast);
        } catch (ClassCastException e) {
            ModConstants.LOGGER.debug("Failed to apply property {}={} for {}.", property.getName(), value, state, e);
            return state;
        }
    }

    private static boolean isMinecraftBootstrapMissing() {
        try {
            return net.neoforged.fml.loading.LoadingModList.get() == null;
        } catch (NoClassDefFoundError ignored) {
            return false;
        }
    }

    @SuppressWarnings("unchecked")
    private static List<StructureTemplate.Palette> getPalettes(StructureTemplate template) {
        if (template instanceof StructureTemplateAccessor accessor) {
            return accessor.getPalettes();
        }
        try {
            var field = StructureTemplate.class.getDeclaredField("palettes");
            field.setAccessible(true);
            return (List<StructureTemplate.Palette>) field.get(template);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Unable to access structure palettes", e);
        }
    }

    private static void setSize(StructureTemplate template, Vec3i size) {
        if (template instanceof StructureTemplateAccessor accessor) {
            accessor.setSize(size);
            return;
        }
        try {
            var field = StructureTemplate.class.getDeclaredField("size");
            field.setAccessible(true);
            field.set(template, size);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Unable to set structure size", e);
        }
    }

    private static StructureTemplate.Palette createPalette(List<StructureTemplate.StructureBlockInfo> blocks) {
        try {
            return StructureTemplateAccessor.wildernessOdysseyApi$createPalette(blocks);
        } catch (Throwable ignored) {
            // Mixin not applied in test environments; fall back to reflective construction.
        }

        try {
            var constructor = StructureTemplate.Palette.class.getDeclaredConstructor(List.class);
            constructor.setAccessible(true);
            return constructor.newInstance(blocks);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Unable to create structure palette", e);
        }
    }
}
