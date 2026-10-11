package com.thunder.wildernessodysseyapi.temporalrift.hybrid;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.thunder.wildernessodysseyapi.structuregen.blueprint.BlueprintParser;
import com.thunder.wildernessodysseyapi.structuregen.validation.BlockStateResolver;
import com.thunder.wildernessodysseyapi.structuregen.validation.BlueprintValidator;
import com.thunder.wildernessodysseyapi.worldgen.structure.rift.RiftRockPlacement;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;

import static org.junit.jupiter.api.Assertions.*;

/** Authored-asset integrity; this does not compile shaders or prove GPU appearance. */
class RiftAssetsTest {
    private static final Path ROOT = Path.of(System.getProperty("wildernessodysseyapi.projectDir", System.getProperty("user.dir")));
    private static final Path ASSETS = ROOT.resolve("src/main/resources/assets/wildernessodysseyapi");

    @Test
    void suppliedAnimationsReferenceExistingBonesAndTexturesHaveMatchingFrames() throws Exception {
        var geometry = json(ASSETS.resolve("geo/anomaly_rift_shell.geo.json")).getAsJsonArray("minecraft:geometry").get(0).getAsJsonObject();
        var bones = new HashSet<String>();
        geometry.getAsJsonArray("bones").forEach(bone -> assertTrue(bones.add(bone.getAsJsonObject().get("name").getAsString()), "Duplicate bone name"));
        var animations = json(ASSETS.resolve("animations/anomaly_rift_shell.animation.json")).getAsJsonObject("animations");
        for (String name : new String[]{"animation.anomaly_rift_shell.idle", "animation.anomaly_rift_shell.critical"}) {
            assertTrue(animations.has(name), "Missing runtime animation " + name);
            assertTrue(animations.getAsJsonObject(name).get("loop").getAsBoolean());
            assertTrue(bones.containsAll(animations.getAsJsonObject(name).getAsJsonObject("bones").keySet()), "Animation references an absent bone");
        }
        int width = geometry.getAsJsonObject("description").get("texture_width").getAsInt();
        int height = geometry.getAsJsonObject("description").get("texture_height").getAsInt();
        for (String texture : new String[]{"anomaly_rift_shell", "anomaly_rift_shell_glow"}) {
            var image = ImageIO.read(ASSETS.resolve("textures/block/" + texture + ".png").toFile());
            assertNotNull(image);
            assertEquals(width, image.getWidth());
            assertEquals(0, image.getHeight() % height);
            assertTrue(image.getHeight() / height > 1, "Supplied texture animation must be retained");
            assertTrue(json(ASSETS.resolve("textures/block/" + texture + ".png.mcmeta")).has("animation"));
        }
    }

    @Test
    void everyRuntimeRockIdHasABoundedNonemptyBlueprintWithLandingBlocks() {
        var validator = new BlueprintValidator((block, properties) -> BlockStateResolver.Resolution.valid());
        assertEquals(8, RiftRockPlacement.VARIANTS.size());
        for (String name : RiftRockPlacement.VARIANTS) {
            var parsed = new BlueprintParser().parse(ROOT.resolve("src/main/structure_blueprints/rift/" + name + ".json"));
            assertFalse(parsed.hasErrors(), () -> parsed.diagnostics().toString());
            var validated = validator.validate(parsed.value());
            assertFalse(validated.hasErrors(), () -> validated.diagnostics().toString());
            var model = validated.value();
            assertEquals(name, model.name());
            assertTrue(model.size().x() <= 9 && model.size().y() <= 8 && model.size().z() <= 9);
            assertTrue(model.blocks().stream().filter(block -> !block.isExplicitAir()).count() > 8);
            assertTrue(model.blocks().stream().anyMatch(block -> block.position().y() == model.size().y() - 1 && !block.isExplicitAir()), "Rock needs a landing surface");
            assertTrue(model.entities().isEmpty(), "Explorable terrain must not introduce entities");
        }
    }

    private static JsonObject json(Path path) throws Exception {
        try (var reader = Files.newBufferedReader(path)) { return JsonParser.parseReader(reader).getAsJsonObject(); }
    }
}
