package com.thunder.wildernessodysseyapi.modpack.structure;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Verifies that generated modpack scaffolds use the Minecraft 1.21.1 data-pack codecs. */
class ModpackStructureRegistryTest {

    @TempDir
    Path temporaryDirectory;

    @Test
    void generatesValidStructurePoolAndSingularTemplatePath() throws Exception {
        ResourceLocation id = ResourceLocation.fromNamespaceAndPath(
                "testpack", "settlements/abandoned_outpost");
        Path source = temporaryDirectory.resolve("abandoned_outpost.nbt");
        Files.write(source, new byte[]{1, 2, 3});

        ModpackStructureRegistry.Definition definition = new ModpackStructureRegistry.Definition();
        definition.structureId = id.toString();
        ModpackStructureRegistry.Entry entry = new ModpackStructureRegistry.Entry(
                id, source, temporaryDirectory.resolve("definition.json"), true, definition, null);
        Path output = temporaryDirectory.resolve("generated");

        // Seed obsolete output to prove regeneration removes only files formerly owned by the generator.
        Path pluralTemplate = output.resolve(
                "data/testpack/structures/settlements/abandoned_outpost.nbt");
        Path obsoleteTag = output.resolve(
                "data/testpack/tags/worldgen/biome/has_structure/settlements/abandoned_outpost.json");
        Files.createDirectories(pluralTemplate.getParent());
        Files.createDirectories(obsoleteTag.getParent());
        Files.write(pluralTemplate, new byte[]{9});
        Files.writeString(obsoleteTag, "{}");

        ModpackStructureRegistry.generateWorldgenScaffold(entry, output);

        assertTrue(Files.exists(output.resolve(
                "data/testpack/structure/settlements/abandoned_outpost.nbt")));
        assertFalse(Files.exists(pluralTemplate));
        assertFalse(Files.exists(obsoleteTag));

        JsonObject structure = readJson(output.resolve(
                "data/testpack/worldgen/structure/settlements/abandoned_outpost.json"));
        assertEquals("testpack:settlements/abandoned_outpost_pool",
                structure.get("start_pool").getAsString());
        assertEquals(0, structure.getAsJsonObject("start_height").get("absolute").getAsInt());
        assertFalse(structure.has("start_pool_inline"));

        JsonObject pool = readJson(output.resolve(
                "data/testpack/worldgen/template_pool/settlements/abandoned_outpost_pool.json"));
        assertEquals("minecraft:single_pool_element",
                pool.getAsJsonArray("elements").get(0).getAsJsonObject()
                        .getAsJsonObject("element").get("element_type").getAsString());
        assertEquals(48, readJson(output.resolve("pack.mcmeta"))
                .getAsJsonObject("pack").get("pack_format").getAsInt());
    }

    private static JsonObject readJson(Path path) throws Exception {
        return JsonParser.parseString(Files.readString(path)).getAsJsonObject();
    }
}
