package com.thunder.wildernessodysseyapi.loottable;

import com.sk89q.worldedit.extent.clipboard.Clipboard;
import com.sk89q.worldedit.extent.clipboard.io.ClipboardFormat;
import com.sk89q.worldedit.extent.clipboard.io.ClipboardFormats;
import com.sk89q.worldedit.extent.clipboard.io.ClipboardReader;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldedit.neoforge.internal.NBTConverter;
import com.sk89q.worldedit.world.block.BaseBlock;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;

import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;

/**
 * Scans structure files for references to loot tables.
 * <p>
 * This utility supports both vanilla NBT structures and WorldEdit schematic files
 * (.schem). Each structure is parsed and any loot tables referenced within block
 * NBT data are collected and returned to the caller.
 */
public class StructureLootScanner {

    /**
     * Searches the given directory for structure files and collects any loot table
     * resource locations referenced within them.
     *
     * @param structuresDir path to a directory containing structure files
     * @return a set of loot table {@link ResourceLocation}s discovered in the
     *     directory, or an empty set if none are found or the directory cannot be
     *     read
     */
    public static Set<ResourceLocation> findReferencedLootTables(Path structuresDir) {
        Set<ResourceLocation> foundLootTables = new HashSet<>();
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(structuresDir)) {
            for (Path path : stream) {
                String name = path.getFileName().toString();
                try {
                    if (name.endsWith(".nbt")) {
                        CompoundTag nbt = NbtIo.readCompressed(path, NbtAccounter.unlimitedHeap());
                        scanTemplate(nbt, foundLootTables);
                    } else if (name.endsWith(".schem")) {
                        ClipboardFormat fmt = ClipboardFormats.findByFile(path.toFile());
                        if (fmt != null) {
                            try (ClipboardReader reader = fmt.getReader(Files.newInputStream(path))) {
                                Clipboard clipboard = reader.read();
                                scanClipboard(clipboard, foundLootTables);
                            }
                        }
                    }
                } catch (Exception e) {
                    System.err.println("[StructureLootScanner] Failed reading structure: " + path + ": " + e.getMessage());
                }
            }
        } catch (IOException e) {
            System.err.println("[StructureLootScanner] Failed listing NBT structures: " + e.getMessage());
        }
        return foundLootTables;
    }

    /**
     * Parses a vanilla structure template and collects any referenced loot tables.
     *
     * @param nbt            the structure's NBT tag
     * @param foundLootTables accumulator for discovered loot tables
     * @throws Exception if reflection access fails or the template cannot be read
     */
    private static void scanTemplate(CompoundTag nbt, Set<ResourceLocation> foundLootTables) throws Exception {
        StructureTemplate template = new StructureTemplate();
        template.load(BuiltInRegistries.BLOCK.asLookup(), nbt);

        Field palettesField = StructureTemplate.class.getDeclaredField("palettes");
        palettesField.setAccessible(true);
        @SuppressWarnings("unchecked")
        var palettes = (java.util.List<StructureTemplate.Palette>) palettesField.get(template);

        for (var palette : palettes) {
            palette.blocks().stream()
                    .filter(info -> info.nbt() != null && info.nbt().contains("LootTable"))
                    .forEach(info -> {
                        String loot = info.nbt().getString("LootTable");
                        ResourceLocation rl = ResourceLocation.tryParse(loot);
                        if (rl != null) {
                            foundLootTables.add(rl);
                        }
                    });
        }
    }

    /**
     * Parses a WorldEdit clipboard and collects any referenced loot tables.
     *
     * @param clipboard      the clipboard to scan
     * @param foundLootTables accumulator for discovered loot tables
     */
    private static void scanClipboard(Clipboard clipboard, Set<ResourceLocation> foundLootTables) {
        for (BlockVector3 vec : clipboard.getRegion()) {
            BaseBlock block = clipboard.getFullBlock(vec);
            var ref = block.getNbtReference();
            if (ref != null) {
                CompoundTag tag = NBTConverter.toNative(ref.getValue());
                if (tag.contains("LootTable")) {
                    ResourceLocation rl = ResourceLocation.tryParse(tag.getString("LootTable"));
                    if (rl != null) {
                        foundLootTables.add(rl);
                    }
                }
            }
        }
    }
}
