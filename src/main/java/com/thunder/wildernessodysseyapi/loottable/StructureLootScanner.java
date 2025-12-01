package com.thunder.wildernessodysseyapi.loottable;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.Tag;

import com.thunder.wildernessodysseyapi.util.NbtParsingUtils;
import net.minecraft.resources.ResourceLocation;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;

/**
 * Scans structure files for references to loot tables.
 * <p>
 * This utility parses vanilla structure templates (NBT files) and collects
 * any loot tables referenced within block entity or entity data.
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
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(structuresDir, "*.nbt")) {
            for (Path path : stream) {
                try {
                    NbtParsingUtils.extendNbtParseTimeout();
                    CompoundTag nbt = NbtIo.readCompressed(path, NbtAccounter.unlimitedHeap());
                    scanTemplate(nbt, foundLootTables);
                } catch (Exception e) {
                    System.err.println("[StructureLootScanner] Failed reading structure: " + path + ": " + e.getMessage());
                }
            }
        } catch (IOException e) {
            System.err.println("[StructureLootScanner] Failed listing structure directory: " + e.getMessage());
        }
        return foundLootTables;
    }

    private static void scanTemplate(CompoundTag nbt, Set<ResourceLocation> foundLootTables) {
        if (nbt.contains("blocks", Tag.TAG_LIST)) {
            ListTag blocks = nbt.getList("blocks", Tag.TAG_COMPOUND);
            for (int i = 0; i < blocks.size(); i++) {
                CompoundTag block = blocks.getCompound(i);
                if (block.contains("nbt", Tag.TAG_COMPOUND)) {
                    addLootTable(block.getCompound("nbt"), foundLootTables);
                }
            }
        }

        if (nbt.contains("entities", Tag.TAG_LIST)) {
            ListTag entities = nbt.getList("entities", Tag.TAG_COMPOUND);
            for (int i = 0; i < entities.size(); i++) {
                CompoundTag entity = entities.getCompound(i);
                if (entity.contains("nbt", Tag.TAG_COMPOUND)) {
                    addLootTable(entity.getCompound("nbt"), foundLootTables);
                }
            }
        }
    }

    private static void addLootTable(CompoundTag tag, Set<ResourceLocation> foundLootTables) {
        if (tag.contains("LootTable", Tag.TAG_STRING)) {
            ResourceLocation rl = ResourceLocation.tryParse(tag.getString("LootTable"));
            if (rl != null) {
                foundLootTables.add(rl);
            }
        }
    }
}
