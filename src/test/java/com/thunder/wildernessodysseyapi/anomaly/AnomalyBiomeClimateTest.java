package com.thunder.wildernessodysseyapi.anomaly;

import com.google.gson.JsonParser;
import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.JsonOps;
import net.minecraft.world.level.biome.Climate;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** Exercises Minecraft's real climate matcher to catch variant slots shadowed by a broader forest slot. */
class AnomalyBiomeClimateTest {
    @Test
    void everyAuthoredZoneCanWinAtItsClimateCenter() throws Exception {
        Path root = Path.of(System.getProperty("wildernessodysseyapi.projectDir", System.getProperty("user.dir")));
        var json = JsonParser.parseString(Files.readString(root.resolve(
                "src/main/resources/data/wildernessodysseyapi/dimension/anomaly_dimension.json")))
                .getAsJsonObject().getAsJsonObject("generator").getAsJsonObject("biome_source");
        List<Pair<Climate.ParameterPoint, String>> points = new ArrayList<>();
        for (var entry : json.getAsJsonArray("biomes")) {
            var slot = entry.getAsJsonObject();
            points.add(Pair.of(Climate.ParameterPoint.CODEC.parse(JsonOps.INSTANCE, slot.get("parameters"))
                    .getOrThrow(), slot.get("biome").getAsString()));
        }
        var matcher = new Climate.ParameterList<>(points);
        Set<String> reachable = new HashSet<>();
        for (var entry : points) {
            var point = entry.getFirst();
            var target = Climate.target(center(point.temperature()), center(point.humidity()),
                    center(point.continentalness()), center(point.erosion()), center(point.depth()), center(point.weirdness()));
            String winner = matcher.findValue(target);
            assertEquals(entry.getSecond(), winner, "A broader slot must not shadow an authored variant");
            reachable.add(winner);
        }
        assertEquals(Set.of("wildernessodysseyapi:anomaly_forest", "wildernessodysseyapi:anomaly_plains",
                "wildernessodysseyapi:anomaly_hills", "wildernessodysseyapi:floating_fringe",
                "wildernessodysseyapi:rift_core"), reachable);
    }

    private static float center(Climate.Parameter parameter) {
        return Climate.unquantizeCoord((parameter.min() + parameter.max()) / 2);
    }
}
