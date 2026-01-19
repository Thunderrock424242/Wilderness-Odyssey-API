package com.thunder.wildernessodysseyapi.gametest;

import com.sk89q.worldedit.extent.clipboard.Clipboard;
import com.sk89q.worldedit.extent.clipboard.io.ClipboardFormat;
import com.sk89q.worldedit.extent.clipboard.io.ClipboardFormats;
import com.sk89q.worldedit.extent.clipboard.io.ClipboardReader;
import com.thunder.wildernessodysseyapi.Core.ModConstants;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Optional;

/**
 * Loads .schem/.schematic GameTest templates by converting clipboards into in-memory StructureTemplates.
 */
public final class SchemGameTestStructureLoader {
    private static final List<String> SUPPORTED_EXTENSIONS = List.of(".schem", ".schematic");
    private static final String STRUCTURE_PREFIX = "structure/";

    private SchemGameTestStructureLoader() {
    }

    public static Optional<StructureTemplate> tryLoad(ResourceManager resourceManager, ResourceLocation id) {
        for (String extension : SUPPORTED_EXTENSIONS) {
            ResourceLocation schemLocation = ResourceLocation.fromNamespaceAndPath(
                    id.getNamespace(), STRUCTURE_PREFIX + id.getPath() + extension);
            Optional<Resource> resource = resourceManager.getResource(schemLocation);
            if (resource.isEmpty()) {
                continue;
            }

            try (InputStream stream = resource.get().open()) {
                Optional<StructureTemplate> template = loadFromStream(extension, stream);
                if (template.isPresent()) {
                    return template;
                }
            } catch (IOException | RuntimeException e) {
                ModConstants.LOGGER.warn("Failed to load schematic-backed test structure {}.", schemLocation, e);
            }
        }

        return Optional.empty();
    }

    static Optional<StructureTemplate> loadFromStream(String extension, InputStream stream) {
        ClipboardFormat format = pickFormat(extension);
        if (format == null) {
            ModConstants.LOGGER.debug("Unknown schematic format for extension {}.", extension);
            return Optional.empty();
        }

        try (ClipboardReader reader = format.getReader(stream)) {
            Clipboard clipboard = reader.read();
            return Optional.of(SchematicClipboardAdapter.toTemplate(clipboard));
        } catch (IOException | RuntimeException e) {
            ModConstants.LOGGER.warn("Failed to read schematic content for extension {}.", extension, e);
            return Optional.empty();
        }
    }

    private static ClipboardFormat pickFormat(String extension) {
        return switch (extension) {
            case ".schem" -> ClipboardFormats.findByAlias("sponge");
            case ".schematic" -> ClipboardFormats.findByAlias("schematic");
            default -> null;
        };
    }
}
