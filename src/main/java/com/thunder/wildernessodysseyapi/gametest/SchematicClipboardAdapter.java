package com.thunder.wildernessodysseyapi.gametest;

import com.sk89q.worldedit.entity.BaseEntity;
import com.sk89q.worldedit.entity.Entity;
import com.sk89q.worldedit.extent.clipboard.Clipboard;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldedit.math.Vector3;
import com.sk89q.worldedit.regions.Region;
import com.sk89q.worldedit.util.Location;
import com.sk89q.worldedit.world.block.BaseBlock;
import com.sk89q.worldedit.world.block.BlockStateHolder;
import com.sk89q.worldedit.neoforge.internal.NBTConverter;
import com.thunder.wildernessodysseyapi.Core.ModConstants;
import com.thunder.wildernessodysseyapi.mixin.StructureTemplateAccessor;
import com.thunder.wildernessodysseyapi.mixin.StructureTemplatePaletteAccessor;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.phys.Vec3;
import org.enginehub.linbus.tree.LinCompoundTag;

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

        for (int x = 0; x < size.x(); x++) {
            for (int y = 0; y < size.y(); y++) {
                for (int z = 0; z < size.z(); z++) {
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
        setSize(template, new Vec3i(size.x(), size.y(), size.z()));

        copyEntities(clipboard, template, min);

        return template;
    }

    private static BlockState convertState(BlockStateHolder<?> holder) {
        if (isMinecraftBootstrapMissing()) {
            return null;
        }

        ResourceLocation blockId = ResourceLocation.tryParse(holder.getBlockType().id());
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
            return StructureTemplatePaletteAccessor.wildernessOdysseyApi$createPalette(blocks);
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

    private static void copyEntities(Clipboard clipboard, StructureTemplate template, BlockVector3 min) {
        List<StructureTemplate.StructureEntityInfo> entities = getEntityInfoList(template);
        if (entities == null) {
            return;
        }

        for (Entity entity : clipboard.getEntities()) {
            if (entity == null) {
                continue;
            }

            Location location = entity.getLocation();
            BaseEntity state = entity.getState();
            if (location == null || state == null || state.getType() == null) {
                continue;
            }

            CompoundTag nbt = toNativeCompound(state.getNbtReference() != null ? state.getNbtReference().getValue() : null);
            if (!nbt.contains("id")) {
                nbt.putString("id", state.getType().id());
            }

            Vector3 position = location.toVector();
            Vec3 relative = new Vec3(
                    position.x() - min.x(),
                    position.y() - min.y(),
                    position.z() - min.z());
            BlockPos blockPos = BlockPos.containing(relative);
            entities.add(new StructureTemplate.StructureEntityInfo(relative, blockPos, nbt));
        }
    }

    private static CompoundTag toNativeCompound(LinCompoundTag tag) {
        if (tag == null) {
            return new CompoundTag();
        }

        try {
            return NBTConverter.toNative(tag);
        } catch (Throwable t) {
            ModConstants.LOGGER.warn("Failed to convert entity NBT from schematic clipboard.", t);
            return new CompoundTag();
        }
    }

    @SuppressWarnings("unchecked")
    private static List<StructureTemplate.StructureEntityInfo> getEntityInfoList(StructureTemplate template) {
        if (template instanceof StructureTemplateAccessor accessor) {
            return accessor.getEntityInfoList();
        }
        try {
            var field = StructureTemplate.class.getDeclaredField("entityInfoList");
            field.setAccessible(true);
            return (List<StructureTemplate.StructureEntityInfo>) field.get(template);
        } catch (ReflectiveOperationException e) {
            ModConstants.LOGGER.warn("Unable to access structure entity list; schematic entities will be skipped.", e);
            return null;
        }
    }
}
