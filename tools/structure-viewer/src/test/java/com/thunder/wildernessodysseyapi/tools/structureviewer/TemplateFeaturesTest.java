package com.thunder.wildernessodysseyapi.tools.structureviewer;

import com.thunder.wildernessodysseyapi.tools.structureviewer.io.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.zip.*;
import static org.junit.jupiter.api.Assertions.*;

class TemplateFeaturesTest {
    @TempDir Path temp;

    @Test void recognizesActualCactusAndRockNamesEvenWhenTheyHaveStructurePools()throws Exception {
        var entries=scan(List.of("cactus_1","cobblestone_rock_2","sandstone_rock_3","stone_rock_4","fallen_palm_tree_2","standing_dark_oak_log","damaged_mushroom_1","small_decoration/boulder"));
        for(var entry:entries)assertTrue(entry.feature(),entry.path());
    }
    @Test void keepsBuildingsAndUncertainNamesVisible()throws Exception {
        var entries=scan(List.of("mushroom_house_1","rock_castle","cactus_temple","rockport","small_decoration/old_well","small_decoration/crumbling_tower","trees/forest_temple","camp_1","ruin_1","mysterious_piece","rock_garden"));
        for(var entry:entries)assertFalse(entry.feature(),entry.path());
    }
    @Test void recognizesExplicitFeatureFoldersWithoutUsingTheModName()throws Exception {
        var entries=scan(List.of("features/unknown_piece","vegetation/unknown_piece","small_decoration/candle_1","decorations/unknown_piece","village/house_1"));
        assertEquals(Set.of("additionalstructures/structure/features/unknown_piece.nbt","additionalstructures/structure/vegetation/unknown_piece.nbt",
                "additionalstructures/structure/small_decoration/candle_1.nbt","additionalstructures/structure/decorations/unknown_piece.nbt"),
                entries.stream().filter(StructureCatalog.Entry::feature).map(StructureCatalog.Entry::path).collect(java.util.stream.Collectors.toSet()));
        var standalone=new StructureCatalog.Entry("rocks-and-cactus.jar","demo/structure/house.nbt",StructureSource.file(temp.resolve("house.nbt")));
        assertFalse(standalone.feature());
    }
    private List<StructureCatalog.Entry> scan(List<String> names)throws Exception {
        Path mods=Files.createDirectories(temp.resolve("mods")),jar=mods.resolve("decorative-mod.jar");
        try(var out=new ZipOutputStream(Files.newOutputStream(jar))) {
            for(String name:names)put(out,"data/additionalstructures/structure/"+name+".nbt",new byte[]{1});
            // These decorations are also registered as jigsaw pool pieces in the real modpack.
            put(out,"data/additionalstructures/worldgen/template_pool/cactus_1.json","{\"elements\":[{\"weight\":1,\"element\":{\"element_type\":\"minecraft:single_pool_element\",\"location\":\"additionalstructures:cactus_1\",\"projection\":\"rigid\",\"processors\":\"minecraft:empty\"}}]}".getBytes(StandardCharsets.UTF_8));
        }
        byte[] original=Files.readAllBytes(jar);
        var catalog=StructureCatalog.scan(temp,temp.resolve("build"));
        assertEquals(names.size(),catalog.entries().size());assertTrue(catalog.diagnostics().isEmpty());assertArrayEquals(original,Files.readAllBytes(jar));
        return catalog.entries();
    }
    private static void put(ZipOutputStream out,String name,byte[] bytes)throws Exception {out.putNextEntry(new ZipEntry(name));out.write(bytes);out.closeEntry();}
}
