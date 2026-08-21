package com.thunder.wildernessodysseyapi.architecture;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Set;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Guards release metadata and Minecraft 1.21 resource paths in the packaged mod JAR. */
class ReleaseArtifactContractTest {

    private static final Pattern DEPENDENCY_BLOCK = Pattern.compile(
            "(?ms)^\\[\\[dependencies\\.wildernessodysseyapi]]\\R(.*?)(?=^\\[\\[|\\z)"
    );
    private static final Set<String> REQUIRED_ENTRIES = Set.of(
            "data/wildernessodysseyapi/recipe/anomaly_gateway.json",
            "data/wildernessodysseyapi/recipe/breathing_mask.json",
            "data/wildernessodysseyapi/recipe/inhaler.json",
            "data/minecraft/tags/block/mineable/pickaxe.json",
            "data/minecraft/tags/block/needs_diamond_tool.json",
            "data/minecraft/tags/item/music_discs.json",
            "data/wildernessodysseyapi/structure/village.nbt",
            "logo.png",
            "assets/wildernessodysseyapi/textures/entity/rift_maw.png",
            "assets/wildernessodysseyapi/textures/entity/rift_listener.png"
    );
    private static final Set<String> OBSOLETE_ENTRIES = Set.of(
            "data/wildernessodysseyapi/recipes/anomaly_gateway.json",
            "data/wildernessodysseyapi/recipes/breathing_mask.json",
            "data/wildernessodysseyapi/recipes/inhaler.json",
            "data/minecraft/tags/blocks/mineable/pickaxe.json",
            "data/minecraft/tags/blocks/needs_diamond_tool.json",
            "data/minecraft/tags/items/music_discs.json"
    );

    @Test
    void packagedMetadataUsesCurrentNeoForgeDependencySchema() throws IOException {
        try (JarFile jar = openBuiltJar()) {
            String metadata = readEntry(jar, "META-INF/neoforge.mods.toml");

            assertFalse(Pattern.compile("(?m)^\\s*mandatory\\s*=").matcher(metadata).find());
            assertFalse(metadata.contains("${"), "Generated metadata still contains an unresolved placeholder");
            assertEquals("[4,)", scalar(metadata, "loaderVersion"));
            assertEquals("All Rights Reserved", scalar(metadata, "license"));
            assertTrue(metadata.contains("displayName=\"Wilderness Odyssey API\""));
            assertFalse(metadata.contains("a api for my modpack"));
            assertDependency(metadata, "minecraft", "required", "[1.21.1,1.22)");
            assertDependency(metadata, "neoforge", "required", "[21.1.0,)");
            assertDependency(metadata, "ticktoklib", "required", "[1.4.0,)");
            assertDependency(metadata, "curios", "optional", "[9.2.0,)");
            assertDependency(metadata, "geckolib", "required", "[4.8.2,)");
            assertDependency(metadata, "create", "required", "[6.0.10,)");
            assertDependency(metadata, "worldedit", "optional", "[7.3.8,)");
            assertDependency(metadata, "spark", "optional", "[1.0.0,)");
            assertFalse(metadata.contains("[[accessTransformers]]"));
            assertTrue(jar.getJarEntry("META-INF/accesstransformer.cfg") == null);
        }
    }

    @Test
    void packagedRecipesAndTagsUseMinecraft121SingularDirectories() throws IOException {
        try (JarFile jar = openBuiltJar()) {
            for (String requiredEntry : REQUIRED_ENTRIES) {
                assertNotNull(jar.getJarEntry(requiredEntry), () -> "Missing packaged resource " + requiredEntry);
            }
            for (String obsoleteEntry : OBSOLETE_ENTRIES) {
                assertTrue(jar.getJarEntry(obsoleteEntry) == null, () -> "Obsolete plural resource path " + obsoleteEntry);
            }
        }
    }

    private static JarFile openBuiltJar() throws IOException {
        String jarPath = System.getProperty("wildernessodysseyapi.jarPath");
        assertNotNull(jarPath, "Gradle must provide wildernessodysseyapi.jarPath");
        return new JarFile(Path.of(jarPath).toFile());
    }

    private static String readEntry(JarFile jar, String name) throws IOException {
        JarEntry entry = jar.getJarEntry(name);
        assertNotNull(entry, () -> "Missing packaged entry " + name);
        try (InputStream input = jar.getInputStream(entry)) {
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private static String scalar(String metadata, String key) {
        Matcher matcher = Pattern.compile("(?m)^" + Pattern.quote(key) + "=\\\"([^\\\"]+)\\\"")
                .matcher(metadata);
        assertTrue(matcher.find(), () -> "Missing metadata key " + key);
        return matcher.group(1);
    }

    private static void assertDependency(String metadata, String modId, String type, String range) {
        Matcher matcher = DEPENDENCY_BLOCK.matcher(metadata);
        while (matcher.find()) {
            String block = matcher.group(1);
            if (block.contains("modId=\"" + modId + "\"")) {
                assertTrue(block.contains("type=\"" + type + "\""), () -> modId + " has wrong dependency type");
                assertTrue(block.contains("versionRange=\"" + range + "\""), () -> modId + " has wrong version range");
                return;
            }
        }
        throw new AssertionError("Missing dependency metadata for " + modId);
    }
}
