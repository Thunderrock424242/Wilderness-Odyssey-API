package com.thunder.wildernessodysseyapi.architecture;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import org.apache.maven.artifact.versioning.DefaultArtifactVersion;
import org.apache.maven.artifact.versioning.InvalidVersionSpecificationException;
import org.apache.maven.artifact.versioning.VersionRange;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Set;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.jar.JarInputStream;
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
            "data/wildernessodysseyapi/structure/bunker.nbt",
            "logo.png",
            "assets/wildernessodysseyapi/textures/entity/rift_maw.png",
            "assets/wildernessodysseyapi/textures/entity/rift_listener.png"
    );
    private static final Set<String> OBSOLETE_ENTRIES = Set.of(
            "data/wildernessodysseyapi/recipes/anomaly_gateway.json",
            "data/wildernessodysseyapi/recipes/breathing_mask.json",
            "data/wildernessodysseyapi/recipes/inhaler.json",
            "data/wildernessodysseyapi/structures/bunker.nbt",
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

    @ParameterizedTest
    @CsvSource({
            "thirst, 1.21.1-2.1.5",
            "eclipticseasons, 0.15.3.1",
            "cold_sweat, 2.4.3.1",
            "sereneseasons, 10.1.0.3-patch11-1"
    })
    void packagedOptionalDependenciesAcceptInstalledVersions(String modId, String installedVersion)
            throws IOException, InvalidVersionSpecificationException {
        try (JarFile jar = openBuiltJar()) {
            Matcher matcher = DEPENDENCY_BLOCK.matcher(readEntry(jar, "META-INF/neoforge.mods.toml"));
            while (matcher.find()) {
                String block = matcher.group(1);
                if (modId.equals(scalar(block, "modId"))) {
                    assertEquals("optional", scalar(block, "type"), modId + " must remain optional");
                    VersionRange range = VersionRange.createFromVersionSpec(scalar(block, "versionRange"));
                    assertTrue(range.containsVersion(new DefaultArtifactVersion(installedVersion)),
                            () -> modId + " rejects installed version " + installedVersion + " with range " + range);
                    return;
                }
            }
            throw new AssertionError("Missing dependency metadata for " + modId);
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

    @ParameterizedTest
    @CsvSource({
            "com.squareup.okhttp3, okhttp-jvm, okhttp3/OkHttpClient.class",
            "com.squareup.okio, okio-jvm, okio/Buffer.class",
            "org.jetbrains.kotlin, kotlin-stdlib, kotlin/jvm/internal/Intrinsics.class",
            "io.github.resilience4j, resilience4j-circuitbreaker, io/github/resilience4j/circuitbreaker/CircuitBreaker.class",
            "io.github.resilience4j, resilience4j-core, io/github/resilience4j/core/functions/Either.class",
            "org.yaml, snakeyaml, org/yaml/snakeyaml/Yaml.class",
            "com.github.luben, zstd-jni, com/github/luben/zstd/Zstd.class"
    })
    void packagedLibrariesCarryRuntimeClassesUnderMavenCoordinates(
            String group, String artifact, String runtimeClass
    ) throws IOException {
        try (JarFile jar = openBuiltJar()) {
            for (JsonElement element : JsonParser.parseString(readEntry(jar, "META-INF/jarjar/metadata.json"))
                    .getAsJsonObject().getAsJsonArray("jars")) {
                var library = element.getAsJsonObject();
                var identifier = library.getAsJsonObject("identifier");
                if (!group.equals(identifier.get("group").getAsString())
                        || !artifact.equals(identifier.get("artifact").getAsString())) {
                    continue;
                }
                JarEntry nestedJar = jar.getJarEntry(library.get("path").getAsString());
                assertNotNull(nestedJar, "Missing bundled JAR for " + group + ":" + artifact);
                try (JarInputStream nested = new JarInputStream(jar.getInputStream(nestedJar))) {
                    for (JarEntry entry; (entry = nested.getNextJarEntry()) != null;) {
                        if (runtimeClass.equals(entry.getName())) {
                            return;
                        }
                    }
                }
                throw new AssertionError("Bundled " + artifact + " is missing " + runtimeClass);
            }
            throw new AssertionError("Missing bundled Maven dependency " + group + ":" + artifact);
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
        Matcher matcher = Pattern.compile("(?m)^" + Pattern.quote(key) + "=\\\"([^\\\"]*)\\\"")
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
