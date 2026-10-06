package com.thunder.wildernessodysseyapi.worldgen.biome;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.serialization.Lifecycle;
import com.mojang.serialization.JsonOps;
import dev.worldgen.lithostitched.api.worldgen.biomeinjector.BiomeInjector;
import dev.worldgen.lithostitched.api.worldgen.biomeinjector.ParameterBuilder;
import dev.worldgen.lithostitched.impl.worldgen.biomeinjector.region.Region;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.MappedRegistry;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.RegistryOps;
import net.minecraft.tags.BiomeTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.biome.Biome;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;

/** Guards the stable Lithostitched regions and the data entry that activates injector callbacks. */
class LithostitchedRegionResourcesTest {

    @Test
    void anomalyAndPolarRegionsTargetTheOverworldWithTheirEstablishedWeights() throws IOException {
        assertRegion("anomaly_overworld", 3);
        assertRegion("polar_glacial_region", 12);
    }

    @Test
    void regionsDeclareTheBiomeSetRequiredByNewerLithostitchedVersions() throws IOException {
        assertOverworldBiomeSet("anomaly_overworld");
        assertOverworldBiomeSet("polar_glacial_region");
    }

    @Test
    void dataRegistryContainsAUsableBiomeInjectorBootstrap() throws IOException {
        String injectorId = "anomaly_forest_from_forest";
        JsonObject injector = readJson("src/main/resources/data/wildernessodysseyapi/"
                + "lithostitched/biome_injector/" + injectorId + ".json");

        assertEquals("lithostitched:replace_partially", injector.get("type").getAsString(), injectorId);
        assertEquals("minecraft:overworld", injector.get("dimension").getAsString(), injectorId);
        assertEquals(500, injector.get("priority").getAsInt(), injectorId);
        assertEquals(1, injector.getAsJsonArray("targets").size(), injectorId);
        assertEquals("minecraft:forest", injector.getAsJsonArray("targets").get(0).getAsString(), injectorId);
        assertEquals("wildernessodysseyapi:anomaly_forest", injector.get("replacement").getAsString(), injectorId);
        assertEquals(0, injector.getAsJsonObject("parameters").size(), injectorId);
        assertEquals("wildernessodysseyapi:anomaly_overworld", injector.get("region").getAsString(), injectorId);
    }

    @Test
    void upperBoundedClimateFilterAvoidsLithostitched165ReversedRange() {
        assertDoesNotThrow(() -> BiomeCompatibilityBootstrap.climateAtMost(
                ParameterBuilder.create(),
                BiomeInjector.ClimateParameter.TEMPERATURE,
                0.35
        ));
    }

    private static void assertRegion(String name, int expectedWeight) throws IOException {
        JsonObject region = readJson("src/main/resources/data/wildernessodysseyapi/"
                + "lithostitched/region/" + name + ".json");
        // Lithostitched 1.8 decodes the biome holder set. Named biome tags need
        // registry-aware ops, as provided by Minecraft's datapack loader.
        MappedRegistry<Biome> biomes = new MappedRegistry<>(Registries.BIOME, Lifecycle.stable());
        biomes.getOrCreateTag(BiomeTags.IS_OVERWORLD);
        var ops = RegistryOps.create(JsonOps.INSTANCE, HolderLookup.Provider.create(Stream.of(biomes.asLookup())));
        Region decoded = Region.CODEC.parse(ops, region).getOrThrow();
        assertEquals("minecraft:overworld", decoded.dimension().location().toString(), name);
        assertEquals(expectedWeight, decoded.weight(), name);
        assertEquals(BiomeTags.IS_OVERWORLD, decoded.biomes().unwrapKey().orElseThrow(), name);
    }

    private static void assertOverworldBiomeSet(String name) throws IOException {
        JsonObject region = readJson("src/main/resources/data/wildernessodysseyapi/"
                + "lithostitched/region/" + name + ".json");
        // Decode the named biome set independently so the resource intent is
        // checked alongside the full registry-aware region codec above.
        TagKey<Biome> biomes = TagKey.hashedCodec(Registries.BIOME)
                .fieldOf("biomes").codec().parse(JsonOps.INSTANCE, region).getOrThrow();
        assertEquals(BiomeTags.IS_OVERWORLD, biomes, name);
    }

    private static JsonObject readJson(String relativePath) throws IOException {
        Path root = Path.of(System.getProperty(
                "wildernessodysseyapi.projectDir", System.getProperty("user.dir")));
        return JsonParser.parseString(Files.readString(root.resolve(relativePath))).getAsJsonObject();
    }
}
