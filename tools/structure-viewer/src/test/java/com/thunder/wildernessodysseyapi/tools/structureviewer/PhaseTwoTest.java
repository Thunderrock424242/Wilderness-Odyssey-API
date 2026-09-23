package com.thunder.wildernessodysseyapi.tools.structureviewer;

import com.thunder.wildernessodysseyapi.tools.structureviewer.assets.*;
import com.thunder.wildernessodysseyapi.tools.structureviewer.io.*;
import com.thunder.wildernessodysseyapi.tools.structureviewer.model.*;
import com.thunder.wildernessodysseyapi.tools.structureviewer.model.StructureData.*;
import com.thunder.wildernessodysseyapi.tools.structureviewer.render.*;
import com.thunder.wildernessodysseyapi.tools.structureviewer.ui.ViewerSettings;
import com.thunder.wildernessodysseyapi.tools.structureviewer.validation.StructureValidation;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.awt.image.BufferedImage;
import javax.imageio.ImageIO;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;

class PhaseTwoTest {
    @TempDir Path temp;

    @Test void readsExistingBlueprintsAndKeepsTypedSnbtAndMetadata()throws Exception{
        Path project=project();
        for(String name:List.of("test_shelter")){
            var data=StructureReaders.read(project.resolve("src/main/structure_blueprints/"+name+".json"));
            assertFalse(data.blocks().isEmpty());assertEquals(name,data.name());assertTrue(data.diagnostics().isEmpty(),data.diagnostics().toString());
        }
        Path file=temp.resolve("typed.json");
        Files.writeString(file,"""
            {"formatVersion":1,"name":"typed","size":[1,1,1],
             "blocks":[{"pos":[0,0,0],"block":"demo:door","properties":{"facing":"north"},
             "blockEntitySnbt":"{id:'demo:door',powered:1b,energy:9000000000L,items:[I;1,2],nested:{label:'test'}}",
             "markers":["mission"]}],"markers":["entry"]}
            """);
        var data=StructureReaders.read(file);var nbt=data.blocks().getFirst().blockEntity().compound();
        assertEquals(1,nbt.get("powered").type());assertEquals(4,nbt.get("energy").type());assertEquals(11,nbt.get("items").type());
        assertEquals("north",data.state(data.blocks().getFirst()).properties().get("facing"));
        assertTrue(data.metadata().get("markers").display().contains("entry"));
    }

    @Test void readsPaletteJsonAndReportsRecoverableMalformedPayloads()throws Exception{
        Path file=temp.resolve("palette.json");
        Files.writeString(file,"""
                {"size":[1,1,1],"palette":[{"Name":"minecraft:stone"}],
                 "blocks":[{"pos":[0,0,0],"state":0},{"pos":[0,0,0],"state":0,"nbt":"broken"},
                 {"pos":[4,0,0],"state":999}],"metadata":{"source":"fixture"}}
                """);
        var data=StructureReaders.read(file);
        var warnings=StructureValidation.inspect(data);
        assertEquals(3,data.blocks().size());
        assertTrue(warnings.stream().anyMatch(s->s.contains("duplicate")));
        assertTrue(warnings.stream().anyMatch(s->s.contains("outside")));
        assertTrue(warnings.stream().anyMatch(s->s.contains("malformed")));
    }

    @Test void rejectsAmbiguousJsonAndMalformedDimensions()throws Exception{
        for(String json:List.of("{\"size\":[1,1,1],\"size\":[2,2,2],\"blocks\":[]}",
                "{\"size\":[1.5,1,1],\"blocks\":[]}","{\"size\":[-1,1,1],\"blocks\":[]}")){
            Path file=temp.resolve("bad.json");Files.writeString(file,json);
            assertThrows(java.io.IOException.class,()->StructureReaders.read(file));
        }
    }

    @Test void resolvesRealVanillaTexturesStairsAndDoorsFromLocalAssets()throws Exception{
        try(var assets=AssetRepository.discover(project(),List.of())){
            org.junit.jupiter.api.Assumptions.assumeTrue(assets.read("assets/minecraft/blockstates/stone.json").isPresent(),"Optional integration check requires locally installed Minecraft 1.21.1 assets");
            var resolver=new BlockModelResolver(assets);
            var stone=resolver.resolve(new BlockState("minecraft:stone",Map.of()));
            assertFalse(stone.fallback(),resolver.diagnostics().toString());assertTrue(stone.occludes());assertEquals(6,stone.quads().size());
            assertTrue(stone.quads().getFirst().texture().width()>=16);
            var stairs=resolver.resolve(new BlockState("minecraft:oak_stairs",Map.of("facing","north","half","bottom","shape","straight","waterlogged","false")));
            assertFalse(stairs.fallback(),resolver.diagnostics().toString());assertFalse(stairs.occludes());assertTrue(stairs.quads().size()>6);
            var door=resolver.resolve(new BlockState("minecraft:oak_door",Map.of("facing","north","half","lower","hinge","left","open","false","powered","false")));
            assertFalse(door.fallback(),resolver.diagnostics().toString());assertFalse(door.occludes());
            assertTrue(door.quads().stream().flatMap(q->q.vertices().stream()).anyMatch(v->v.x()>0&&v.x()<1||v.z()>0&&v.z()<1));
        }
    }

    @Test void resolvesParentTexturesMultipartAndReportsMissingAndInvalidAssets()throws Exception{
        pack();
        write("assets/demo/blockstates/test.json","""
            {"multipart":[{"apply":{"model":"demo:block/child"}},
             {"when":{"OR":[{"facing":"north"},{"AND":[{"powered":"true"},{"facing":"south"}]}]},
              "apply":{"model":"demo:block/child","y":90}}]}
            """);
        try(var assets=new AssetRepository(List.of(temp))){
            var resolver=new BlockModelResolver(assets);
            var model=resolver.resolve(new BlockState("demo:test",Map.of("facing","north")));
            assertFalse(model.fallback(),resolver.diagnostics().toString());assertEquals(12,model.quads().size());
            var single=resolver.resolve(new BlockState("demo:test",Map.of("facing","east")));
            assertEquals(6,single.quads().size());assertTrue(resolver.diagnostics().stream().anyMatch(s->s.contains("Invalid asset-defined")));
            assertTrue(resolver.resolve(new BlockState("missing:block",Map.of())).fallback());
            assertTrue(resolver.diagnostics().stream().anyMatch(s->s.contains("Missing blockstates")));
        }
    }

    @Test void catchesParentCyclesMissingTexturesAndCustomLoadersWithoutCrashing()throws Exception{
        pack();
        write("assets/demo/blockstates/test.json","{\"variants\":{\"\":{\"model\":\"demo:block/child\"}}}");
        write("assets/demo/models/block/child.json","{\"parent\":\"demo:block/child\"}");
        try(var assets=new AssetRepository(List.of(temp))){
            var resolver=new BlockModelResolver(assets);assertTrue(resolver.resolve(new BlockState("demo:test",Map.of())).fallback());
            assertTrue(resolver.diagnostics().stream().anyMatch(s->s.contains("Cyclic")));
        }
        write("assets/demo/models/block/child.json","{\"parent\":\"demo:block/base\",\"textures\":{\"all\":\"demo:block/absent\"}}");
        try(var assets=new AssetRepository(List.of(temp))){
            var resolver=new BlockModelResolver(assets);assertFalse(resolver.resolve(new BlockState("demo:test",Map.of())).fallback());
            assertTrue(resolver.diagnostics().stream().anyMatch(s->s.contains("Missing texture")));
        }
        write("assets/demo/models/block/child.json","{\"loader\":\"demo:java_renderer\"}");
        try(var assets=new AssetRepository(List.of(temp))){
            assertTrue(new BlockModelResolver(assets).resolve(new BlockState("demo:test",Map.of())).fallback());
        }
    }

    @Test void outlinesSeparateAdjacentSameMaterialBlocksWithoutChangingPicking()throws Exception{
        var data=new StructureData("two",temp,new Position(2,1,1),List.of(
                new Block(new Position(0,0,0),0,null,Map.of()),new Block(new Position(1,0,0),0,null,Map.of())),
                List.of(List.of(new BlockState("minecraft:stone",Map.of()))),List.of(),Map.of(),List.of());
        var mesh=BlockMesh.build(data,false,-1);
        var camera=new Camera.View(new Vec3(1,.5,-3),new Vec3(0,0,1),new Vec3(1,0,0),new Vec3(0,1,0),false,2);
        var renderer=new SoftwareRenderer();
        var plain=renderer.render(mesh,camera,400,300,-1,false,false,false,true);
        var edges=renderer.render(mesh,camera,400,300,-1,false,false,true,true);
        assertArrayEquals(plain.blockIds(),edges.blockIds());
        assertNotEquals(plain.image().getRGB(199,150),edges.image().getRGB(199,150));
        assertEquals(0,edges.pick(170,150));assertEquals(1,edges.pick(230,150));
        assertTrue(RenderQuality.ULTRA.scale(1000,700)>RenderQuality.FAST.scale(1000,700));
    }

    @Test void usesFirstAssetLayerAndRejectsDirectoryTraversal()throws Exception{
        Path high=temp.resolve("high"),low=temp.resolve("low");
        Files.createDirectories(high.resolve("assets/demo"));Files.createDirectories(low.resolve("assets/demo"));
        Files.writeString(high.resolve("assets/demo/a.json"),"high");Files.writeString(low.resolve("assets/demo/a.json"),"low");
        try(var assets=new AssetRepository(List.of(high,low))){
            assertEquals("high",new String(assets.read("assets/demo/a.json").orElseThrow()));
            assertThrows(java.io.IOException.class,()->assets.read("assets/../secret"));
        }
    }

    @Test void savesManualQualityAndDetectsAtomicFileReplacement()throws Exception{
        Path settings=temp.resolve("settings.properties");
        var saved=new ViewerSettings(RenderQuality.ULTRA,true,true,false,List.of(temp));
        saved.save(settings);assertEquals(saved,ViewerSettings.read(settings));
        Path file=temp.resolve("watched.json");Files.writeString(file,"first");
        CountDownLatch changed=new CountDownLatch(1);
        List<String> errors=new CopyOnWriteArrayList<>();
        try(var watcher=new StructureFileWatcher(file,changed::countDown,errors::add)){
            Path replacement=temp.resolve("replacement.json");Files.writeString(replacement,"second");
            Files.move(replacement,file,StandardCopyOption.REPLACE_EXISTING);
            assertTrue(changed.await(5,TimeUnit.SECONDS),"Atomic replacement must trigger reload");
        }
        assertTrue(errors.isEmpty(),errors.toString());
    }

    @Test void reusesCachedCatalogDefaultsWithoutReplacingExplicitProperties()throws Exception{
        Path build=temp.resolve("build"),catalog=build.resolve("generated/structuregen/catalog/available-content.json");
        Files.createDirectories(catalog.getParent());
        Files.writeString(catalog,"""
            {"blocks":[{"id":"demo:door","defaultProperties":{"facing":"north","open":"false"},
             "properties":{"facing":["north","south"],"open":["false","true"]}}]}
            """);
        var snapshot=BlockStateCatalog.discover(temp,build);List<String> warnings=new ArrayList<>();
        var state=snapshot.complete(new BlockState("demo:door",Map.of("facing","south")),warnings::add);
        assertEquals(Map.of("facing","south","open","false"),state.properties());assertTrue(warnings.isEmpty());
        snapshot.complete(new BlockState("demo:door",Map.of("facing","up")),warnings::add);assertFalse(warnings.isEmpty());
        assertTrue(snapshot.description().contains("not revalidated"));
    }

    @Test void malformedCachedDefaultsCannotPreventAValidModelPreview()throws Exception{
        pack();
        write("assets/demo/blockstates/test.json","{\"variants\":{\"\":{\"model\":\"demo:block/child\"}}}");
        Path build=temp.resolve("build"),catalog=build.resolve("generated/structuregen/catalog/available-content.json");
        Files.createDirectories(catalog.getParent());
        Files.writeString(catalog,"{\"blocks\":[{\"id\":\"demo:test\",\"defaultProperties\":[]}] }");
        try(var assets=new AssetRepository(List.of(temp))){
            var resolver=new BlockModelResolver(assets,BlockStateCatalog.discover(temp,build));
            assertFalse(resolver.resolve(new BlockState("demo:test",Map.of())).fallback());
            assertTrue(resolver.diagnostics().stream().anyMatch(s->s.contains("catalog")));
        }
    }

    @Test void stackedPartialModelsKeepFacesAcrossTheEmptyGap()throws Exception{
        pack();
        write("assets/demo/blockstates/test.json","{\"variants\":{\"\":{\"model\":\"demo:block/child\"}}}");
        Path base=temp.resolve("assets/demo/models/block/base.json");
        Files.writeString(base,Files.readString(base).replace("\"down\":{\"texture\":\"#all\"}",
                "\"down\":{\"texture\":\"#all\",\"cullface\":\"down\"}"));
        var data=new StructureData("slabs",temp,new Position(1,2,1),List.of(
                new Block(new Position(0,0,0),0,null,Map.of()),new Block(new Position(0,1,0),0,null,Map.of())),
                List.of(List.of(new BlockState("demo:test",Map.of()))),List.of(),Map.of(),List.of());
        try(var assets=new AssetRepository(List.of(temp))){
            var mesh=BlockMesh.build(data,false,-1,new BlockModelResolver(assets),List.of());
            assertEquals(12,mesh.faces().size(),"Half-height blocks have air between them; keep the upper block's bottom face.");
        }
    }
    private void pack()throws Exception{
        write("assets/demo/models/block/base.json","""
            {"textures":{"all":"demo:block/color"},"elements":[{"from":[0,0,0],"to":[16,8,16],"faces":{
            "north":{"texture":"#all"},"south":{"texture":"#all"},"west":{"texture":"#all"},"east":{"texture":"#all"},
            "up":{"texture":"#all"},"down":{"texture":"#all"}}}]}
            """);
        write("assets/demo/models/block/child.json","{\"parent\":\"demo:block/base\",\"textures\":{\"all\":\"demo:block/color\"}}");
        BufferedImage image=new BufferedImage(16,16,BufferedImage.TYPE_INT_ARGB);
        for(int y=0;y<16;y++)for(int x=0;x<16;x++)image.setRGB(x,y,(x<8?0xffef4040:0xff40ef40));
        Path png=temp.resolve("assets/demo/textures/block/color.png");Files.createDirectories(png.getParent());ImageIO.write(image,"png",png.toFile());
    }
    private void write(String relative,String contents)throws Exception{Path file=temp.resolve(relative);Files.createDirectories(file.getParent());Files.writeString(file,contents);}
    private static Path project(){return Path.of(System.getProperty("structureViewer.projectDir"));}
}
