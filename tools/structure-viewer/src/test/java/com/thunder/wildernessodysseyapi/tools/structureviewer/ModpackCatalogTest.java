package com.thunder.wildernessodysseyapi.tools.structureviewer;

import com.thunder.wildernessodysseyapi.tools.structureviewer.io.*;
import com.thunder.wildernessodysseyapi.tools.structureviewer.assets.*;
import com.thunder.wildernessodysseyapi.tools.structureviewer.render.BlockMesh;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.util.*;
import java.util.zip.*;
import static org.junit.jupiter.api.Assertions.*;

class ModpackCatalogTest {
    @TempDir Path temp;
    @Test void groupsDuplicateResourceIdsByArchiveAndIgnoresDefinitionsAndBrokenJars() throws Exception {
        Path mods = Files.createDirectories(temp.resolve("mods"));
        for (String name : List.of("first.jar", "second.jar")) writeJar(mods.resolve(name));
        Files.writeString(mods.resolve("broken.jar"), "not a zip");
        var result = StructureCatalog.scan(temp,temp.resolve("build"));
        assertEquals(4,result.entries().size()); assertEquals(3,result.archives());
        assertEquals(2,result.entries().stream().map(StructureCatalog.Entry::group).distinct().count());
        assertTrue(result.diagnostics().stream().anyMatch(s -> s.contains("broken.jar")));
        for (var entry : result.entries()) {
            byte[] before = Files.readAllBytes(entry.source().container());
            var data = entry.source().read();
            assertFalse(data.blocks().isEmpty());
            assertEquals(entry.source().container(),data.source());
            assertArrayEquals(before,Files.readAllBytes(entry.source().container()));
        }
        // Closed archive handles permit replacing the mod after previewing it.
        Files.move(mods.resolve("first.jar"),mods.resolve("replacement.jar"));
    }
    @Test void refusesArchiveTraversal() {
        assertThrows(IllegalArgumentException.class,() -> new StructureSource(temp.resolve("a.jar"),"data/demo/../../escape.nbt"));
        assertFalse(StructureCatalog.isTemplate("data/demo/tags/worldgen/structure/test.json"));
        assertFalse(StructureCatalog.isTemplate("data/demo/worldgen/structure/test.json"));
    }
    @Test void externalModpackCannotInheritAnotherProjectsGeneratedStructuresOrDefaults() throws Exception {
        Path foreign=temp.resolve("other-project/build"),pack=Files.createDirectories(temp.resolve("pack/mods")).getParent();
        Path template=foreign.resolve("generated/structuregen/resources/data/demo/structure/foreign.nbt");
        Files.createDirectories(template.getParent());FixtureNbt.write(template,FixtureNbt.structure(),true);
        Path catalog=foreign.resolve("generated/structuregen/catalog/available-content.json");Files.createDirectories(catalog.getParent());
        Files.writeString(catalog,"{\"blocks\":[{\"id\":\"demo:foreign\",\"defaultProperties\":{\"open\":\"true\"}}]}");
        assertTrue(StructureCatalog.scan(pack,foreign).entries().isEmpty());
        var state=new com.thunder.wildernessodysseyapi.tools.structureviewer.model.StructureData.BlockState("demo:foreign",Map.of());
        assertEquals(state,BlockStateCatalog.discover(pack,foreign).complete(state,message->{}));
    }
    @Test void findsNearbyAndLauncherInstanceLayouts() throws Exception {
        Path instance = Files.createDirectories(temp.resolve("instance/.minecraft/mods"));
        assertEquals(instance.getParent(),ModpackLocation.normalize(temp.resolve("instance")));
        assertEquals(instance.getParent(),ModpackLocation.normalize(instance));
        assertEquals(instance.getParent(),ModpackLocation.nearby(instance.getParent().resolve("Viewer/app")).orElseThrow());
        assertTrue(ModpackLocation.nearby(temp.resolve("unrelated")).isEmpty());
    }
    @Test void startsAtCurseForgeInstancesWithoutOverridingAnotherSelectedPack() throws Exception {
        Path user=Files.createDirectories(temp.resolve("user"));
        Path instances=Files.createDirectories(user.resolve("curseforge/minecraft/Instances"));
        Path instance=Files.createDirectories(instances.resolve("My Pack/mods")).getParent();
        Path other=Files.createDirectories(temp.resolve("other/mods")).getParent();
        assertEquals(instances,ModpackLocation.chooserDirectory(temp.resolve("empty"),user));
        assertEquals(instances,ModpackLocation.chooserDirectory(instance,user));
        assertEquals(other,ModpackLocation.chooserDirectory(other,user));
        assertEquals(instances,ModpackLocation.chooserDirectory(instance.resolve("mods"),user));
        Path separateUser=Files.createDirectories(temp.resolve("separate-user"));
        assertEquals(separateUser,ModpackLocation.chooserDirectory(temp.resolve("empty"),separateUser));
    }
    @Test void resolvesModJarAssetsAndReusesThemAfterTheArchiveCloses() throws Exception {
        Path mods=Files.createDirectories(temp.resolve("mods")),jar=mods.resolve("assets.jar");
        var png=new java.io.ByteArrayOutputStream();
        var image=new java.awt.image.BufferedImage(1,1,java.awt.image.BufferedImage.TYPE_INT_ARGB);
        image.setRGB(0,0,0xff34abcd);javax.imageio.ImageIO.write(image,"png",png);
        try(var out=new ZipOutputStream(Files.newOutputStream(jar))) {
            put(out,"data/demo/structure/room.json","""
                {"size":[1,2,1],"blocks":[{"pos":[0,0,0],"block":"demo:cached"},{"pos":[0,1,0],"block":"demo:cached"}]}
                """.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            put(out,"assets/demo/blockstates/cached.json","{\"variants\":{\"\":{\"model\":\"demo:block/cached\"}}}".getBytes());
            put(out,"assets/demo/models/block/cached.json","""
                {"textures":{"all":"demo:block/cached"},"elements":[{"from":[0,0,0],"to":[16,16,16],"faces":{
                "north":{"texture":"#all"},"south":{"texture":"#all"},"west":{"texture":"#all"},
                "east":{"texture":"#all"},"up":{"texture":"#all"},"down":{"texture":"#all"}}}]}
                """.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            put(out,"assets/demo/textures/block/cached.png",png.toByteArray());
        }
        var data=new StructureSource(jar,"data/demo/structure/room.json").read();
        ResolvedModels models;
        try(var assets=AssetRepository.discover(temp,List.of())) {
            models=ResolvedModels.capture(data,new BlockModelResolver(assets),assets.sources());
        }
        assertEquals(1,models.resolved());assertEquals(0,models.missing());
        var all=BlockMesh.build(data,false,-1,models);
        Files.move(jar,mods.resolve("moved.jar"));
        var layer=BlockMesh.build(data,false,1,models);
        assertEquals(6,layer.faces().size());
        assertTrue(layer.faces().stream().allMatch(face->face.blockIndex()==1));
        assertSame(all.faces().getFirst().quad().texture(),layer.faces().getFirst().quad().texture());
        assertEquals(0xff34abcd,layer.faces().getFirst().quad().texture().sample(0,0));
    }
    private void writeJar(Path file) throws Exception {
        Path nbt = FixtureNbt.write(temp.resolve("source.nbt"),FixtureNbt.structure(),true);
        try (ZipOutputStream out = new ZipOutputStream(Files.newOutputStream(file))) {
            put(out,"data/demo/structure/house.nbt",Files.readAllBytes(nbt));
            put(out,"data/demo/structures/room.json","{\"size\":[1,1,1],\"blocks\":[{\"pos\":[0,0,0],\"block\":\"minecraft:stone\"}]}".getBytes(java.nio.charset.StandardCharsets.UTF_8));
            put(out,"data/demo/tags/worldgen/structure/ignored.json","{}".getBytes());
            put(out,"data/demo/worldgen/structure/ignored.json","{}".getBytes());
        }
    }
    @Test void resolvesModelsAndTexturesAcrossModsWithoutLeakingBetweenPacks() throws Exception {
        Path first=Files.createDirectories(temp.resolve("first/mods")).getParent();
        Path second=Files.createDirectories(temp.resolve("second/mods")).getParent();
        for(Path pack:List.of(first,second)) {
            try(var out=new ZipOutputStream(Files.newOutputStream(pack.resolve("mods/structure.jar")))) {
                put(out,"data/demo/structure/room.json","{\"size\":[1,1,1],\"blocks\":[{\"pos\":[0,0,0],\"block\":\"demo:panel\"}]}".getBytes());
                put(out,"assets/demo/blockstates/panel.json","{\"variants\":{\"\":{\"model\":\"demo:block/panel\"}}}".getBytes());
                put(out,"assets/demo/models/block/panel.json","{\"parent\":\"library:block/slab\",\"textures\":{\"all\":\"materials:block/panel\"}}".getBytes());
            }
            try(var out=new ZipOutputStream(Files.newOutputStream(pack.resolve("mods/library.jar")))) {
                put(out,"assets/library/models/block/slab.json","""
                    {"elements":[{"from":[0,0,0],"to":[16,8,16],"faces":{
                    "north":{"texture":"#all"},"south":{"texture":"#all"},"west":{"texture":"#all"},
                    "east":{"texture":"#all"},"up":{"texture":"#all"},"down":{"texture":"#all"}}}]}
                    """.getBytes());
            }
            var image=new java.awt.image.BufferedImage(1,1,java.awt.image.BufferedImage.TYPE_INT_ARGB);
            image.setRGB(0,0,pack.equals(first)?0xff34abcd:0xffcd5634);var png=new java.io.ByteArrayOutputStream();javax.imageio.ImageIO.write(image,"png",png);
            try(var out=new ZipOutputStream(Files.newOutputStream(pack.resolve("mods/materials.jar")))) {put(out,"assets/materials/textures/block/panel.png",png.toByteArray());}
            var data=StructureCatalog.scan(pack,temp.resolve("build")).entries().getFirst().source().read();
            try(var assets=AssetRepository.discover(pack,List.of())) {
                var models=ResolvedModels.capture(data,new BlockModelResolver(assets),assets.sources());
                assertEquals(1,models.resolved(),models.diagnostics().toString());assertEquals(0,models.missing());
                var model=models.models().get(data.state(data.blocks().getFirst()));
                assertFalse(model.occludes());assertFalse(model.fallback());
                assertTrue(model.quads().stream().flatMap(q->q.vertices().stream()).allMatch(vertex->vertex.y()<=.5));
                assertEquals(pack.equals(first)?0xff34abcd:0xffcd5634,model.quads().getFirst().texture().sample(0,0));
            }
        }
    }
    @Test void usesCurseForgeInstallAssetsForVanillaParentsInModModels()throws Exception {
        Path minecraft=Files.createDirectories(temp.resolve("curseforge/minecraft"));
        Path pack=Files.createDirectories(minecraft.resolve("Instances/My Pack/mods")).getParent();
        Path client=minecraft.resolve("Install/versions/1.21.1/1.21.1.jar");Files.createDirectories(client.getParent());
        try(var out=new ZipOutputStream(Files.newOutputStream(client))) {put(out,"assets/minecraft/models/block/curseforge_fixture.json","{\"elements\":[]}".getBytes());}
        try(var assets=AssetRepository.discover(pack,List.of())) {
            assertTrue(assets.read("assets/minecraft/models/block/curseforge_fixture.json").isPresent());
        }
    }
    private static void put(ZipOutputStream out,String name,byte[] bytes) throws Exception { out.putNextEntry(new ZipEntry(name));out.write(bytes);out.closeEntry(); }
}
