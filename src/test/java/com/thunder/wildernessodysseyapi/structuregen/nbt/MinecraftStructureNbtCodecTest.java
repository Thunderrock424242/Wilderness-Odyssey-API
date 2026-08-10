package com.thunder.wildernessodysseyapi.structuregen.nbt;

import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.thunder.wildernessodysseyapi.structuregen.comparison.StructureComparator;
import com.thunder.wildernessodysseyapi.structuregen.comparison.StructureComparisonReport;
import com.thunder.wildernessodysseyapi.structuregen.model.StructureBlock;
import com.thunder.wildernessodysseyapi.structuregen.model.StructureBlockState;
import com.thunder.wildernessodysseyapi.structuregen.model.StructureEntity;
import com.thunder.wildernessodysseyapi.structuregen.model.StructureModel;
import com.thunder.wildernessodysseyapi.structuregen.model.StructurePosition;
import com.thunder.wildernessodysseyapi.structuregen.model.StructureSize;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.TagParser;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Covers deterministic compressed NBT compilation and loss-aware canonical-model round trips.
 */
class MinecraftStructureNbtCodecTest {

    @TempDir
    Path tempDirectory;

    @Test
    void writesDeterministicCompressedNbtAndPreservesSupportedAndUnknownFields()
            throws IOException, CommandSyntaxException {
        StructureModel expected = fixtureModel();
        Path firstOutput = tempDirectory.resolve("first.nbt");
        Path secondOutput = tempDirectory.resolve("second.nbt");
        MinecraftStructureNbtWriter writer = new MinecraftStructureNbtWriter();

        writer.writeCompressed(expected, firstOutput);
        writer.writeCompressed(expected, secondOutput);

        assertArrayEquals(
                Files.readAllBytes(firstOutput),
                Files.readAllBytes(secondOutput),
                "The same canonical model must produce byte-for-byte stable compressed NBT"
        );

        StructureModel reread = new MinecraftStructureNbtReader().read(firstOutput, expected.name());
        StructureComparisonReport comparison = new StructureComparator().compare(expected, reread);

        assertTrue(comparison.semanticallyMatches(), () -> new StructureComparator().format(comparison));
        assertEquals(expected.metadata(), reread.metadata());
        assertEquals(expected.markers(), reread.markers());
        assertEquals(1, reread.sourcePalettes().size());
        assertEquals(2, reread.sourcePalettes().getFirst().size());
        assertEquals(List.of("loot_anchor", "entry_side"), blockAt(reread, 1, 0, 0).markers());
        assertEquals("east", blockAt(reread, 0, 0, 0).state().properties().get("facing"));
        assertEquals("bottom", blockAt(reread, 0, 0, 0).state().properties().get("half"));

        // Imported unknown compounds are intentionally surfaced as SNBT instead of discarded.
        assertEquals(3, parsed(reread.rawRootSnbt()).getCompound("custom_root").getInt("revision"));
        assertEquals(
                1,
                parsed(reread.rawRootSnbt())
                        .getCompound("structuregen")
                        .getCompound("custom_extension")
                        .getByte("enabled")
        );
        assertEquals(
                "preserved",
                parsed(blockAt(reread, 1, 0, 0).rawEntrySnbt()).getString("custom_block")
        );
        assertEquals(5, parsed(reread.entities().getFirst().rawEntrySnbt()).getShort("custom_entity"));
        assertEquals(
                7,
                parsed(state(reread, "minecraft:oak_stairs").rawPaletteEntrySnbt())
                        .getByte("custom_palette")
        );
        assertNotNull(blockAt(reread, 1, 0, 0).blockEntitySnbt());
        assertEquals(
                "minecraft:chest",
                parsed(blockAt(reread, 1, 0, 0).blockEntitySnbt()).getString("id")
        );
        assertEquals(
                "minecraft:armor_stand",
                parsed(reread.entities().getFirst().entityNbtSnbt()).getString("id")
        );

        assertTrue(reread.unsupportedFields().contains("root.custom_root"));
        assertTrue(reread.unsupportedFields().stream().anyMatch(field -> field.endsWith(".custom_palette")));
        assertTrue(reread.unsupportedFields().stream().anyMatch(field -> field.endsWith(".custom_block")));
        assertTrue(reread.unsupportedFields().stream().anyMatch(field -> field.endsWith(".custom_entity")));
    }

    private StructureModel fixtureModel() {
        StructureBlockState stairs = new StructureBlockState(
                "minecraft:oak_stairs",
                Map.of("facing", "east", "half", "bottom", "shape", "straight", "waterlogged", "false"),
                "{custom_palette:7b}"
        );
        StructureBlockState chest = new StructureBlockState(
                "minecraft:chest",
                Map.of("facing", "north", "type", "single", "waterlogged", "false"),
                "{custom_palette:2b}"
        );
        StructureBlock markedChest = new StructureBlock(
                new StructurePosition(1, 0, 0),
                chest,
                "{id:'minecraft:chest',CustomName:'\"Round-trip fixture\"',Items:[]}",
                List.of("loot_anchor", "entry_side"),
                "{custom_block:'preserved'}",
                -1
        );
        StructureBlock stair = new StructureBlock(
                new StructurePosition(0, 0, 0),
                stairs,
                null,
                List.of("entry_step"),
                null,
                -1
        );
        StructureEntity armorStand = new StructureEntity(
                List.of(1.5D, 1.0D, 0.5D),
                new StructurePosition(1, 1, 0),
                "{id:'minecraft:armor_stand',Invisible:1b,NoGravity:1b}",
                "{custom_entity:5s}"
        );
        return new StructureModel(
                "codec_fixture",
                new StructureSize(3, 3, 2),
                List.of(markedChest, stair),
                List.of(armorStand),
                3955,
                Map.of("author", "StructureGen test", "purpose", "round_trip"),
                List.of("fixture", "deterministic"),
                List.of(),
                "{custom_root:{revision:3},structuregen:{custom_extension:{enabled:1b}}}",
                List.of()
        );
    }

    private StructureBlock blockAt(StructureModel model, int x, int y, int z) {
        StructurePosition expectedPosition = new StructurePosition(x, y, z);
        return model.blocks().stream()
                .filter(block -> block.position().equals(expectedPosition))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Missing block at " + expectedPosition.display()));
    }

    private StructureBlockState state(StructureModel model, String blockId) {
        return model.sourcePalettes().getFirst().stream()
                .filter(entry -> entry.blockId().equals(blockId))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Missing palette state " + blockId));
    }

    private CompoundTag parsed(String snbt) throws CommandSyntaxException {
        assertNotNull(snbt, "Expected preserved SNBT");
        return TagParser.parseTag(snbt);
    }
}
