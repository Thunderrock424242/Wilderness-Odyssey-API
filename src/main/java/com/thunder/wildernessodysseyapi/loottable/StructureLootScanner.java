package com.thunder.wildernessodysseyapi.loottable;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;

public class StructureLootScanner {

    public static Set<ResourceLocation> findReferencedLootTables(Path structuresDir) {
        Set<ResourceLocation> found = new HashSet<>();
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(structuresDir, "*.nbt")) {
            for (Path path : stream) {
                try {
                    CompoundTag nbt = NbtIo.readCompressed(path.toFile());
                    StructureTemplate template = new StructureTemplate();
                    template.load(nbt);

                    template.palettes().forEach(palette -> {
                        palette.stream()
                                .filter(info -> info.nbt != null && info.nbt.contains("LootTable"))
                                .forEach(info -> {
                                    String loot = info.nbt.getString("LootTable");
                                    found.add(new ResourceLocation(loot));
                                });
                    });
                } catch (Exception e) {
                    System.err.println("[StructureLootScanner] Failed reading structure: " + path + ": " + e.getMessage());
                }
            }
        } catch (IOException e) {
            System.err.println("[StructureLootScanner] Failed listing NBT structures: " + e.getMessage());
        }
        return found;
    }
}