package com.thunder.wildernessodysseyapi.WorldGen.structure;

import com.mojang.datafixers.util.Pair;
import com.natamus.collective_common_neoforge.schematic.ParsedSchematicObject;
import com.sk89q.worldedit.extent.clipboard.Clipboard;
import com.sk89q.worldedit.extent.clipboard.io.ClipboardFormat;
import com.sk89q.worldedit.extent.clipboard.io.ClipboardFormats;
import com.sk89q.worldedit.extent.clipboard.io.ClipboardReader;
import com.sk89q.worldedit.math.BlockVector3;
import com.thunder.wildernessodysseyapi.Core.ModConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;

/**
 * Captures starter structure schematic metadata from either .schem or .nbt sources so the placement
 * pipeline can operate on a unified abstraction.
 */
public final class StarterStructureSchematic {
    public enum Format {
        SCHEM,
        NBT,
        UNKNOWN
    }

    private final Path path;
    private final Format format;
    private final ClipboardFormat clipboardFormat;
    private final ParsedSchematicObject parsed;
    private final StarterStructureTerrainBlender.Footprint footprint;
    private final List<Pair<BlockPos, Entity>> entities;
    private final boolean shouldSpawnEntities;

    private StarterStructureSchematic(Path path,
                                      Format format,
                                      ClipboardFormat clipboardFormat,
                                      ParsedSchematicObject parsed,
                                      StarterStructureTerrainBlender.Footprint footprint,
                                      List<Pair<BlockPos, Entity>> entities) {
        this.path = path;
        this.format = format;
        this.clipboardFormat = clipboardFormat;
        this.parsed = parsed;
        this.footprint = footprint;
        this.entities = entities == null ? List.of() : List.copyOf(entities);
        this.shouldSpawnEntities = !this.entities.isEmpty();
    }

    public static StarterStructureSchematic capture(ServerLevel level, Path path, BlockPos structurePos,
                                                    ParsedSchematicObject parsed) {
        Format format = detectFormat(path);
        ClipboardFormat clipboardFormat = detectClipboardFormat(path, format);
        if (format == Format.UNKNOWN && clipboardFormat != null) {
            format = inferFormatFromClipboard(clipboardFormat);
        }
        StarterStructureTerrainBlender.Footprint footprint = readFootprint(parsed, clipboardFormat, path);
        List<Pair<BlockPos, Entity>> entities = readEntities(level, path, structurePos, parsed, format);
        if (footprint == null) {
            footprint = deriveFootprintFromEntities(entities, structurePos);
        }

        return new StarterStructureSchematic(path, format, clipboardFormat, parsed, footprint, entities);
    }

    public Path path() {
        return path;
    }

    public Format format() {
        return format;
    }

    public ClipboardFormat clipboardFormat() {
        return clipboardFormat;
    }

    public ParsedSchematicObject parsed() {
        return parsed;
    }

    public StarterStructureTerrainBlender.Footprint footprint() {
        return footprint;
    }

    public List<Pair<BlockPos, Entity>> entities() {
        return entities;
    }

    public boolean shouldSpawnEntities() {
        return shouldSpawnEntities;
    }

    /**
     * Clears parsed schematic buffers so Starter Structure does not attempt to paste a second time
     * after WorldEdit already handled placement.
     */
    public void clearParsedAfterWorldEdit() {
        if (parsed == null) {
            return;
        }
        parsed.blocks = List.of();
        parsed.blockEntityPositions = List.of();
        parsed.entities = List.of();
        parsed.parsedCorrectly = false;
    }

    private static Format detectFormat(Path path) {
        if (path == null) {
            return Format.UNKNOWN;
        }

        String fileName = path.getFileName().toString().toLowerCase(Locale.ROOT);
        if (fileName.endsWith(".schem")) {
            return Format.SCHEM;
        }
        if (fileName.endsWith(".nbt")) {
            return Format.NBT;
        }
        return Format.UNKNOWN;
    }

    private static ClipboardFormat detectClipboardFormat(Path path, Format format) {
        if (path == null) {
            return null;
        }

        ClipboardFormat detected = ClipboardFormats.findByFile(path.toFile());
        if (detected != null) {
            return detected;
        }

        if (format == Format.NBT) {
            return ClipboardFormats.findByAlias("schematic");
        }

        return null;
    }

    private static Format inferFormatFromClipboard(ClipboardFormat clipboardFormat) {
        for (String alias : clipboardFormat.getAliases()) {
            String lower = alias.toLowerCase(Locale.ROOT);
            if (lower.contains("schem") || lower.contains("schematic")) {
                return Format.SCHEM;
            }
            if (lower.contains("nbt")) {
                return Format.NBT;
            }
        }
        return Format.UNKNOWN;
    }

    private static StarterStructureTerrainBlender.Footprint readFootprint(ParsedSchematicObject parsed,
                                                                          ClipboardFormat clipboardFormat,
                                                                          Path path) {
        StarterStructureTerrainBlender.Footprint footprint = tryReadFootprintFromParsed(parsed);
        if (footprint != null) {
            return footprint;
        }

        if (clipboardFormat == null || path == null || !Files.isRegularFile(path)) {
            return null;
        }

        try (InputStream in = Files.newInputStream(path);
             ClipboardReader reader = clipboardFormat.getReader(in)) {
            Clipboard clipboard = reader.read();
            BlockVector3 dimensions = clipboard.getDimensions();
            if (dimensions.getX() > 0 && dimensions.getY() > 0 && dimensions.getZ() > 0) {
                return new StarterStructureTerrainBlender.Footprint(
                        dimensions.getX(),
                        dimensions.getY(),
                        dimensions.getZ());
            }
        } catch (Exception e) {
            ModConstants.LOGGER.debug("[Starter Structure compat] Failed to read schematic dimensions from {}.", path, e);
        }

        return null;
    }

    private static StarterStructureTerrainBlender.Footprint tryReadFootprintFromParsed(ParsedSchematicObject parsed) {
        if (parsed == null) {
            return null;
        }

        try {
            int width = tryReadDimension(parsed, "getWidth", "width");
            int height = tryReadDimension(parsed, "getHeight", "height");
            int length = tryReadDimension(parsed, "getLength", "length");
            if (width > 0 && height > 0 && length > 0) {
                return new StarterStructureTerrainBlender.Footprint(width, height, length);
            }
        } catch (Exception e) {
            ModConstants.LOGGER.debug("[Starter Structure compat] Failed to read schematic dimensions for blending.", e);
        }

        return null;
    }

    private static int tryReadDimension(ParsedSchematicObject parsed, String getterName, String fieldName) throws Exception {
        try {
            java.lang.reflect.Method getter = parsed.getClass().getMethod(getterName);
            getter.setAccessible(true);
            Object result = getter.invoke(parsed);
            if (result instanceof Number number) {
                return number.intValue();
            }
        } catch (NoSuchMethodException ignored) {
            // Fall back to field lookup
        }

        java.lang.reflect.Field field = parsed.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        Object value = field.get(parsed);
        if (value instanceof Number number) {
            return number.intValue();
        }
        return -1;
    }

    private static List<Pair<BlockPos, Entity>> readEntities(ServerLevel level, Path path, BlockPos structurePos,
                                                             ParsedSchematicObject parsed, Format format) {
        if (parsed != null && parsed.entities != null && !parsed.entities.isEmpty()) {
            return List.copyOf(parsed.entities);
        }

        if (format == Format.SCHEM) {
            return SchematicEntityRestorer.backfillEntitiesFromSchem(level, path, false, structurePos, parsed);
        }

        if (format == Format.NBT) {
            return SchematicEntityRestorer.extractEntitiesFromNbt(level, path, structurePos);
        }

        return List.of();
    }

    private static StarterStructureTerrainBlender.Footprint deriveFootprintFromEntities(List<Pair<BlockPos, Entity>> entities, BlockPos origin) {
        if (entities == null || entities.isEmpty() || origin == null) {
            return null;
        }

        int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE, minZ = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE, maxY = Integer.MIN_VALUE, maxZ = Integer.MIN_VALUE;

        for (Pair<BlockPos, Entity> entry : entities) {
            if (entry == null || entry.getFirst() == null) {
                continue;
            }
            BlockPos rel = entry.getFirst().subtract(origin);
            minX = Math.min(minX, rel.getX());
            minY = Math.min(minY, rel.getY());
            minZ = Math.min(minZ, rel.getZ());
            maxX = Math.max(maxX, rel.getX());
            maxY = Math.max(maxY, rel.getY());
            maxZ = Math.max(maxZ, rel.getZ());
        }

        if (minX == Integer.MAX_VALUE) {
            return null;
        }

        int width = (maxX - minX) + 1;
        int height = (maxY - minY) + 1;
        int length = (maxZ - minZ) + 1;
        if (width <= 0 || height <= 0 || length <= 0) {
            return null;
        }

        return new StarterStructureTerrainBlender.Footprint(width, height, length);
    }
}
