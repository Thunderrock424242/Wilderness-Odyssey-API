package com.thunder.wildernessodysseyapi.watersystem.water.worldgen.river.bank;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class RiverBankProfilesTest {
    private static final Set<ResourceLocation> BEDS = Set.of(id("minecraft:gravel"), id("minecraft:clay"));
    private static final Set<ResourceLocation> PLANTS = Set.of(id("minecraft:fern"));

    @AfterEach
    void releasePublishedTestProfiles() {
        RiverBankProfiles.reload(Map.of(), BEDS::contains, PLANTS::contains);
    }

    @Test
    void selectsTheHighestPriorityMatchingBiomeProfileWithAStableTieBreak() {
        var profiles = RiverBankProfiles.parse(Map.of(
                id("test:forest"), profile(10, "minecraft:is_forest"),
                id("test:jungle"), profile(20, "minecraft:is_jungle"),
                id("test:another_jungle"), profile(20, "minecraft:is_jungle")), BEDS::contains, PLANTS::contains);
        var selected = profiles.select(id("minecraft:jungle"), tag -> true);
        assertNotNull(selected);
        assertEquals(id("test:another_jungle"), selected.id());
        assertNull(profiles.select(id("minecraft:desert"), tag -> false));
    }

    @Test
    void weightedNativePaletteIsDeterministicAndReloadInputCannotMutateIt() {
        var json = profile(10, "minecraft:is_forest");
        var profiles = RiverBankProfiles.parse(Map.of(id("test:forest"), json), BEDS::contains, PLANTS::contains);
        json.getAsJsonObject().getAsJsonArray("bed").get(0).getAsJsonObject().addProperty("block", "minecraft:lava");
        var selected = profiles.select(id("minecraft:forest"), tag -> true);
        assertEquals(id("minecraft:gravel"), selected.bed(0));
        assertEquals(id("minecraft:gravel"), selected.bed(2));
        assertEquals(id("minecraft:clay"), selected.bed(3));
        assertEquals(id("minecraft:clay"), selected.bed(-1));
        Set<ResourceLocation> seen = new java.util.HashSet<>();
        for (long seed = -30; seed < 30; seed++) {
            seen.add(selected.bed(seed));
            assertEquals(id("minecraft:fern"), selected.plant(seed));
        }
        assertEquals(BEDS, seen);
        assertThrows(UnsupportedOperationException.class, () -> profiles.profiles().clear());
    }

    @Test
    void unsafeUnknownOrUnboundedDefinitionsRejectTheWholeReloadGeneration() {
        for (String replacement : new String[]{
                "\"block\":\"minecraft:lava\",\"weight\":3",
                "\"block\":\"minecraft:gravel\",\"weight\":0",
                "\"block\":\"minecraft:gravel\",\"weight\":1001"}) {
            String json = profile(10, "minecraft:is_forest").toString()
                    .replace("\"block\":\"minecraft:gravel\",\"weight\":3", replacement);
            assertThrows(IllegalArgumentException.class, () -> RiverBankProfiles.parse(
                    Map.of(id("test:invalid"), JsonParser.parseString(json)), BEDS::contains, PLANTS::contains));
        }
        var unknownField = profile(10, "minecraft:is_forest");
        unknownField.getAsJsonObject().addProperty("run_arbitrary_feature", true);
        assertThrows(IllegalArgumentException.class, () -> RiverBankProfiles.parse(
                Map.of(id("test:invalid"), unknownField), BEDS::contains, PLANTS::contains));
        var unsupportedPlant = profile(10, "minecraft:is_forest");
        unsupportedPlant.getAsJsonObject().getAsJsonArray("plants").get(0).getAsJsonObject()
                .addProperty("block", "minecraft:oak_sapling");
        assertThrows(IllegalArgumentException.class, () -> RiverBankProfiles.parse(
                Map.of(id("test:invalid"), unsupportedPlant), BEDS::contains, PLANTS::contains));
    }

    @Test
    void anInvalidReloadDoesNotReplaceThePreviouslyPublishedImmutableProfiles() {
        RiverBankProfiles.reload(Map.of(id("test:valid"), profile(10, "minecraft:is_forest")), BEDS::contains, PLANTS::contains);
        var previous = RiverBankProfiles.snapshot();
        assertThrows(IllegalArgumentException.class, () -> RiverBankProfiles.reload(
                Map.of(id("test:invalid"), JsonParser.parseString("{}")), BEDS::contains, PLANTS::contains));
        assertSame(previous, RiverBankProfiles.snapshot());
    }

    private static JsonElement profile(int priority, String biomeTag) {
        return JsonParser.parseString("""
                {"priority": %d, "biome_tags": ["%s"],
                 "bed": [{"block":"minecraft:gravel","weight":3},{"block":"minecraft:clay","weight":1}],
                 "plants": [{"block":"minecraft:fern","weight":1}]}
                """.formatted(priority, biomeTag));
    }

    private static ResourceLocation id(String value) { return ResourceLocation.parse(value); }
}
