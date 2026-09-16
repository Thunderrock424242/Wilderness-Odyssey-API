package com.thunder.wildernessodysseyapi.tools.structureviewer;

import com.thunder.wildernessodysseyapi.tools.structureviewer.io.*;
import com.thunder.wildernessodysseyapi.tools.structureviewer.model.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class NbtStructureReaderTest {
    @TempDir Path temp;

    @Test void importsRawAndCompressedWithoutLosingModStatesEntitiesOrMarkers() throws Exception {
        for (boolean compressed : new boolean[]{false,true}) {
            var path = FixtureNbt.write(temp.resolve("structure.nbt"), FixtureNbt.structure(), compressed);
            var data = new NbtStructureReader().read(path);
            assertEquals(new StructureData.Position(2,2,2), data.size());
            assertEquals(2,data.blocks().size());
            assertEquals("wildernessodysseyapi:unknown_door",data.state(data.blocks().get(1)).id());
            assertEquals("north",data.state(data.blocks().get(1)).properties().get("facing"));
            assertEquals(1,data.entities().size());
            assertEquals(1,data.blocks().getFirst().blockEntity().compound().get("power").type());
            assertTrue(data.metadata().get("structuregen").display().contains("mission:start"));
            assertTrue(data.diagnostics().isEmpty());
        }
    }

    @Test void recoversInvalidPaletteAndOutOfBoundsBlock() throws Exception {
        var root = new LinkedHashMap<>(FixtureNbt.structure().compound());
        root.put("blocks",FixtureNbt.tag(9,List.of(FixtureNbt.block(-1,0,0,999),FixtureNbt.block(0,0,0,0))));
        var data = new NbtStructureReader().read(FixtureNbt.write(temp.resolve("bad.nbt"),FixtureNbt.compound(root),true));
        assertEquals(2,data.blocks().size());
        assertEquals(StructureData.BlockState.MISSING,data.state(data.blocks().getFirst()));
        assertTrue(data.diagnostics().stream().anyMatch(d -> d.contains("outside")));
        assertTrue(data.diagnostics().stream().anyMatch(d -> d.contains("palette reference")));
    }

    @Test void preservesAlternativePalettesAndEveryArrayType() throws Exception {
        var root = new LinkedHashMap<>(FixtureNbt.structure().compound());
        var primary = root.remove("palette");
        root.put("palettes", FixtureNbt.tag(9,List.of(primary,primary)));
        root.put("arrays",FixtureNbt.compound(Map.of(
                "bytes",FixtureNbt.tag(7,List.of(FixtureNbt.tag(1,(byte)7))),
                "ints",FixtureNbt.tag(11,List.of(FixtureNbt.tag(3,300))),
                "longs",FixtureNbt.tag(12,List.of(FixtureNbt.tag(4,9000000000L))))));
        var data = new NbtStructureReader().read(FixtureNbt.write(temp.resolve("palettes.nbt"),FixtureNbt.compound(root),false));
        assertEquals(2,data.palettes().size());
        assertEquals(12,data.metadata().get("arrays").compound().get("longs").type());
        assertEquals(9000000000L,data.metadata().get("arrays").compound().get("longs").list().getFirst().value());
    }

    @Test void rejectsTruncatedUnknownAndExcessiveInputsAsReadErrors() throws Exception {
        Path path = temp.resolve("unreadable.nbt");
        for (byte[] bytes : List.of(new byte[]{10,0,0,9},new byte[]{10,0,0,99,0,0})) {
            Files.write(path,bytes);
            assertThrows(IOException.class,() -> new NbtStructureReader().read(path));
        }
        try(var out = new DataOutputStream(Files.newOutputStream(path))) {
            out.writeByte(10);out.writeUTF("");out.writeByte(9);out.writeUTF("blocks");out.writeByte(10);out.writeInt(Integer.MAX_VALUE);
        }
        assertThrows(IOException.class,() -> new NbtStructureReader().read(path));
    }

    @Test void opensExistingBunkerWithoutChangingItsBytes() throws Exception {
        Path path = project().resolve("src/main/resources/data/wildernessodysseyapi/structures/bunker.nbt");
        byte[] before = Files.readAllBytes(path);
        var data = new NbtStructureReader().read(path);
        assertTrue(data.blocks().size() > 1000);
        assertTrue(data.size().x() > 0 && data.size().y() > 0 && data.size().z() > 0);
        assertTrue(data.palettes().getFirst().size() > 1);
        assertArrayEquals(before,Files.readAllBytes(path));
        System.out.println("Existing bunker: " + data.size() + ", " + data.blocks().size() + " blocks, "
                + data.palettes().getFirst().size() + " palette entries");
    }

    @Test void discoversBothResourceConventionsAndIgnoresUnrelatedNbt() throws Exception {
        for (String file : List.of("data/demo/structure/a.nbt","data/demo/structures/b.nbt","assets/irrelevant.nbt")) {
            Path path = temp.resolve(file);Files.createDirectories(path.getParent());Files.write(path,new byte[0]);
        }
        assertEquals(2,StructureDiscovery.discover(List.of(temp,temp)).size());
        assertTrue(StructureDiscovery.discover(StructureDiscovery.roots(project(),project().resolve(".codex-build")))
                .stream().anyMatch(p -> p.getFileName().toString().equals("bunker.nbt")));
    }

    private static Path project() { return Path.of(System.getProperty("structureViewer.projectDir")); }
}

