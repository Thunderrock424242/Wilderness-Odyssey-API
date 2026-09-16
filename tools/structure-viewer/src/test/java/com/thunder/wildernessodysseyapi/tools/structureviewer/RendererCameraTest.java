package com.thunder.wildernessodysseyapi.tools.structureviewer;

import com.thunder.wildernessodysseyapi.tools.structureviewer.model.StructureData;
import com.thunder.wildernessodysseyapi.tools.structureviewer.model.StructureData.*;
import com.thunder.wildernessodysseyapi.tools.structureviewer.render.*;
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
}

