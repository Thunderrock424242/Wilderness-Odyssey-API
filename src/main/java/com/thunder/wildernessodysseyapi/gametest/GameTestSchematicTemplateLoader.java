package com.thunder.wildernessodysseyapi.gametest;

import com.sk89q.worldedit.extent.clipboard.Clipboard;
import com.sk89q.worldedit.extent.clipboard.io.ClipboardFormat;
import com.sk89q.worldedit.extent.clipboard.io.ClipboardFormats;
import com.sk89q.worldedit.extent.clipboard.io.ClipboardReader;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldedit.world.block.BaseBlock;
import com.thunder.wildernessodysseyapi.Core.ModConstants;
import com.thunder.wildernessodysseyapi.mixin.StructureTemplateAccessor;
import com.thunder.wildernessodysseyapi.mixin.StructureTemplateManagerAccessor;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate.Palette;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate.StructureBlockInfo;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager;
import org.jetbrains.annotations.Nullable;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Loads GameTest structure templates from WorldEdit schematic formats so tests can reuse the same
 * schematics without duplicating NBT templates.
 */
public final class GameTestSchematicTemplateLoader {
    private static final List<String> STRUCTURE_FOLDERS = List.of("structure", "structures");
    private static final List<String> SUPPORTED_EXTENSIONS = List.of(".schem", ".schematic");

    private GameTestSchematicTemplateLoader() {
    }

    /**
     * Attempts to load the requested GameTest template from a WorldEdit schematic. When a schematic is
     * found and parsed, the resulting {@link StructureTemplate} is written into the template manager's cache.
     *
     * @return a populated structure template, or {@code null} when no schematic could be resolved
     */
    public static @Nullable StructureTemplate loadFromSchematic(StructureTemplateManager manager, ResourceLocation id) {
        ResourceManager resourceManager = ((StructureTemplateManagerAccessor) manager).getResourceManager();
        if (resourceManager == null) {
            return null;
        }

        byte[] schematicBytes = readSchematicBytes(resourceManager, id);
        if (schematicBytes == null) {
            return null;
        }

        ClipboardFormat format = ClipboardFormats.findByInputStream(() -> new ByteArrayInputStream(schematicBytes));
        if (format == null) {
            ModConstants.LOGGER.warn("[GameTest schematics] Could not detect schematic format for {} ({} bytes).", id, schematicBytes.length);
            return null;
        }

        try (ClipboardReader reader = format.getReader(new ByteArrayInputStream(schematicBytes))) {
            Clipboard clipboard = reader.read();
            StructureTemplate template = manager.getOrCreate(id);
            writeClipboardToTemplate(template, clipboard);
            return template;
        } catch (IOException e) {
            ModConstants.LOGGER.warn("[GameTest schematics] Failed to read schematic for {}.", id, e);
            return null;
        }
    }

    private static @Nullable byte[] readSchematicBytes(ResourceManager resourceManager, ResourceLocation id) {
        for (String folder : STRUCTURE_FOLDERS) {
            for (String extension : SUPPORTED_EXTENSIONS) {
                ResourceLocation schematicLocation = ResourceLocation.fromNamespaceAndPath(
                        id.getNamespace(), folder + "/" + id.getPath() + extension);
                Optional<Resource> resource = resourceManager.getResource(schematicLocation);
                if (resource.isEmpty()) {
                    continue;
                }

                try (InputStream in = resource.get().open()) {
                    return in.readAllBytes();
                } catch (IOException e) {
                    ModConstants.LOGGER.warn("[GameTest schematics] Unable to read schematic resource {}.", schematicLocation, e);
                }
            }
        }

        return null;
    }

    private static void writeClipboardToTemplate(StructureTemplate template, Clipboard clipboard) {
        BlockVector3 min = clipboard.getMinimumPoint();
        BlockVector3 dimensions = clipboard.getDimensions();
        int sizeX = dimensions.getX();
        int sizeY = dimensions.getY();
        int sizeZ = dimensions.getZ();

        List<StructureBlockInfo> blocks = new ArrayList<>();
        for (int x = 0; x < sizeX; x++) {
            for (int y = 0; y < sizeY; y++) {
                for (int z = 0; z < sizeZ; z++) {
                    BlockVector3 cursor = BlockVector3.at(min.getX() + x, min.getY() + y, min.getZ() + z);
                    BaseBlock baseBlock = clipboard.getFullBlock(cursor);
                    if (baseBlock == null) {
                        continue;
                    }

                    BlockState blockState = com.sk89q.worldedit.neoforge.NeoForgeAdapter.adapt(baseBlock.toImmutableState());
                    if (blockState.isAir()) {
                        continue;
                    }

                    BlockPos relativePos = new BlockPos(x, y, z);
                    // Block entity data is intentionally omitted for GameTests; WorldEdit will paste the full
                    // schematic during the test run itself.
                    blocks.add(new StructureBlockInfo(relativePos, blockState, (CompoundTag) null));
                }
            }
        }

        StructureTemplateAccessor accessor = (StructureTemplateAccessor) template;
        accessor.setSize(new Vec3i(sizeX, sizeY, sizeZ));
        List<Palette> palettes = accessor.getPalettes();
        palettes.clear();
        palettes.add(com.thunder.wildernessodysseyapi.mixin.StructureTemplatePaletteInvoker.wildernessodysseyapi$newPalette(blocks));
        accessor.getEntityInfoList().clear();
    }
}
