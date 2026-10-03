package com.thunder.wildernessodysseyapi.tools.structureviewer;

import com.thunder.wildernessodysseyapi.tools.structureviewer.model.StructureData;
import com.thunder.wildernessodysseyapi.tools.structureviewer.model.StructureData.*;
import com.thunder.wildernessodysseyapi.tools.structureviewer.render.*;
import com.thunder.wildernessodysseyapi.tools.structureviewer.assets.*;
import org.junit.jupiter.api.Test;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

class RendererCameraTest {
    private StructureData scene(List<Block> blocks) {
        return new StructureData("scene",Path.of("scene.nbt"),new Position(4,4,4),blocks,
                List.of(List.of(new BlockState("minecraft:stone",Map.of()), new BlockState("minecraft:air",Map.of()))),
                List.of(),Map.of(),List.of());
    }
    private Block block(int x,int y,int z) { return new Block(new Position(x,y,z),0,null,Map.of()); }

    @Test void hidesInteriorFacesAndKeepsSourceIndicesAfterFilteringAir() {
        var data = scene(List.of(new Block(new Position(3,0,0),1,null,Map.of()),block(0,0,0),block(1,0,0)));
        var mesh = BlockMesh.build(data,false,-1);
        assertEquals(10,mesh.faces().size());
        assertTrue(mesh.faces().stream().allMatch(f -> f.blockIndex() == 1 || f.blockIndex() == 2));
        assertEquals(16,BlockMesh.build(data,true,-1).faces().size());
        assertEquals(0,BlockMesh.build(data,false,2).faces().size());
    }

    @Test void picksTheNearestVisibleBlockInsteadOfTheLastDrawnBlock() {
        var mesh = BlockMesh.build(scene(List.of(block(0,0,0),block(0,0,2))),false,-1);
        var camera = new Camera.View(new Vec3(0.5,0.5,-3),new Vec3(0,0,1),
                new Vec3(1,0,0),new Vec3(0,1,0),false,8);
        var frame = new SoftwareRenderer().render(mesh,camera,320,240,-1,false,false);
        assertEquals(0,frame.pick(160,120));
        assertEquals(-1,frame.pick(0,0));
        assertEquals(-1,frame.pick(-1,0));
    }

    @Test void canSeeAWallFromInsideAndClipsCrossingFaces() {
        var mesh = BlockMesh.build(scene(List.of(block(0,0,1),block(1,0,0))),false,-1);
        var camera = new Camera.View(new Vec3(0.9,0.5,0.5),new Vec3(0,0,1),
                new Vec3(1,0,0),new Vec3(0,1,0),false,8);
        var frame = new SoftwareRenderer().render(mesh,camera,320,240,-1,false,false);
        assertEquals(0,frame.pick(160,120));
        assertTrue(java.util.Arrays.stream(frame.blockIds()).anyMatch(i -> i == 1));
    }

    @Test void translucentFacesKeepTheirOwnVerticesWhenOpaqueFacesReuseScratchStorage() {
        BlockState glass=new BlockState("demo:glass",Map.of()),stone=new BlockState("demo:stone",Map.of());
        var data=new StructureData("glass",Path.of("glass.json"),new Position(2,1,1),List.of(
                new Block(new Position(0,0,0),0,null,Map.of()),new Block(new Position(1,0,0),1,null,Map.of())),
                List.of(List.of(glass,stone)),List.of(),Map.of(),List.of());
        var vertices=List.of(new Vec3(0,0,0),new Vec3(0,1,0),new Vec3(1,1,0),new Vec3(1,0,0));
        var transparent=new BlockModel(List.of(new BlockModel.Quad(vertices,new double[]{0,16,0,0,16,0,16,16},
                new Texture(1,1,new int[]{0x8034abcd},true,false),0xffffff,-1,false)),false,false);
        var opaque=new BlockModel(List.of(new BlockModel.Quad(vertices,new double[]{0,16,0,0,16,0,16,16},
                Texture.solid(0xabcdef),0xffffff,-1,false)),false,false);
        var models=new ResolvedModels(Map.of(glass,transparent,stone,opaque),List.of(),2,0,List.of());
        var camera=new Camera.View(new Vec3(1,.5,-3),new Vec3(0,0,1),new Vec3(1,0,0),new Vec3(0,1,0),false,2);
        var frame=new SoftwareRenderer().render(BlockMesh.build(data,false,-1,models),camera,400,300,-1,false,false);
        assertEquals(0,frame.pick(170,150));assertEquals(1,frame.pick(230,150));
    }

    @Test void keepsInsetFacesWhenAnOpaqueNeighborDoesNotTouchThem() {
        BlockState slab=new BlockState("demo:slab",Map.of()),stone=new BlockState("demo:stone",Map.of());
        var data=new StructureData("gap",Path.of("gap.json"),new Position(1,2,1),List.of(
                new Block(new Position(0,0,0),0,null,Map.of()),new Block(new Position(0,1,0),1,null,Map.of())),
                List.of(List.of(slab,stone)),List.of(),Map.of(),List.of());
        var top=new BlockModel.Quad(List.of(new Vec3(0,.5,1),new Vec3(1,.5,1),new Vec3(1,.5,0),new Vec3(0,.5,0)),
                new double[]{0,0,16,0,16,16,0,16},Texture.solid(0xabcdef),0xffffff,3,true);
        var models=new ResolvedModels(Map.of(slab,new BlockModel(List.of(top),false,false)),List.of(),1,1,List.of());
        var mesh=BlockMesh.build(data,false,-1,models);
        assertTrue(mesh.faces().stream().anyMatch(face -> face.blockIndex()==0 && face.quad()==top),
                "A solid block one layer above must not erase the inset slab top across an air gap.");
    }

    @Test void solidCubeHasNoInteriorPixelHolesAcrossAnglesAndResolutions() {
        var blocks=new java.util.ArrayList<Block>();
        for(int y=0;y<4;y++)for(int z=0;z<4;z++)for(int x=0;x<4;x++)blocks.add(block(x,y,z));
        var mesh=BlockMesh.build(scene(blocks),false,-1);
        for(int width:List.of(128,320,513))for(int angle=0;angle<8;angle++) {
            Camera camera=new Camera();camera.focus(mesh);camera.look(angle*130,angle%2==0?0:35);
            var frame=new SoftwareRenderer().render(mesh,camera.view(),width,width*3/4,-1,false,false);
            int coveredRows=0;
            for(int y=0;y<frame.image().getHeight();y++) {
                int first=-1,last=-1;
                for(int x=0;x<width;x++)if(frame.pick(x,y)>=0){if(first<0)first=x;last=x;}
                if(first<0)continue;
                coveredRows++;
                for(int x=first;x<=last;x++)assertTrue(frame.pick(x,y)>=0,"Hole inside a solid cube at "+x+","+y+", width "+width+", angle "+angle);
            }
            assertTrue(coveredRows>10,"Solid cube should remain visible.");
        }
    }

    @Test void freeCameraMovesAndPrecisionSpeedWheelAndOrbitBehave() {
        Camera camera = new Camera();camera.focus(new Position(10,10,10));
        var initial = camera.view();
        camera.move(1,0,0,false,0.1);
        assertEquals(initial.position(),camera.view().position());
        camera.setOrbit(false);
        camera.move(1,0,0,false,0.1);
        var moved = camera.view();
        assertTrue(moved.position().subtract(initial.position()).dot(initial.forward()) > 0);
        Vec3 before = moved.position();
        camera.move(0,1,0,true,0.1);
        assertEquals(moved.speed() * 0.1 * 0.15,
                camera.view().position().subtract(before).dot(moved.right()),0.00001);
        camera.wheel(-1);assertTrue(camera.view().speed() > moved.speed());
        var oldForward = camera.view().forward();
        camera.look(20,10);assertNotEquals(oldForward,camera.view().forward());
        camera.setOrbit(true);
        before = camera.view().position();camera.look(20,10);
        assertNotEquals(before,camera.view().position());
    }

    @Test void framesOccupiedBlocksWithoutPaddingOrCoordinateOverflow() {
        for(int x:List.of(100,Integer.MAX_VALUE)) {
            Camera camera=new Camera();
            camera.focus(BlockMesh.build(scene(List.of(block(x,2,3))),false,-1));
            var view=camera.view();
            Vec3 center=new Vec3(x+.5,2.5,3.5);
            assertEquals(6,Math.sqrt(view.position().subtract(center).dot(view.position().subtract(center))),.000001);
            var projected=view.transform(center);
            assertEquals(0,projected.x(),.000001);assertEquals(0,projected.y(),.000001);
            assertTrue(projected.z()>0);
        }
    }
}

