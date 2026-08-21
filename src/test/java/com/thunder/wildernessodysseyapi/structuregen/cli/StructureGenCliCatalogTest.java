package com.thunder.wildernessodysseyapi.structuregen.cli;

import com.thunder.wildernessodysseyapi.structuregen.content.JsonSnapshotStructureBlockCatalog;
import com.thunder.wildernessodysseyapi.structuregen.content.StructureBlockCatalog;
import com.thunder.wildernessodysseyapi.structuregen.content.VanillaStructureBlockCatalog;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Covers the catalog freshness and filesystem-link boundary used by generation. */
class StructureGenCliCatalogTest {

    private static final String CURRENT_FINGERPRINT = "a".repeat(64);
    private static final String STALE_FINGERPRINT = "b".repeat(64);

    @TempDir
    Path tempDirectory;

    @Test
    void generationRequiresCatalogFingerprint() {
        Path projectRoot = tempDirectory.resolve("project-missing-fingerprint").toAbsolutePath().normalize();
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> StructureGenCli.execute(CliArguments.parse(new String[]{
                        "generate",
                        "--project-dir", projectRoot.toString(),
                        "--blueprints", projectRoot.resolve("src/main/structure_blueprints").toString(),
                        "--output-resources",
                        projectRoot.resolve("build/generated/structuregen/resources").toString()
                }))
        );
        assertTrue(exception.getMessage().contains("Missing required option --catalog-fingerprint"));
    }

    @Test
    void usesMatchingSnapshotAndFallsBackToVanillaWhenFingerprintIsStale() throws IOException {
        Path projectRoot = tempDirectory.resolve("project");
        Path snapshot = catalogPath(projectRoot);
        writeSnapshot(snapshot, CURRENT_FINGERPRINT);

        StructureBlockCatalog matching = StructureGenCli.loadContentCatalog(
                projectRoot, snapshot.toString(), CURRENT_FINGERPRINT
        );
        assertInstanceOf(JsonSnapshotStructureBlockCatalog.class, matching);

        StructureBlockCatalog stale = StructureGenCli.loadContentCatalog(
                projectRoot, snapshot.toString(), STALE_FINGERPRINT
        );
        assertInstanceOf(VanillaStructureBlockCatalog.class, stale);
        assertTrue(stale.blocks().keySet().stream().allMatch(id -> "minecraft".equals(id.getNamespace())));
    }

    @Test
    void acceptsCatalogFromTaskSpecificIsolatedBuildRoot() throws IOException {
        Path projectRoot = tempDirectory.resolve("isolated-catalog-project");
        Path snapshot = projectRoot.resolve(
                ".codex-build/generated/structuregen/catalog/available-content.json");
        writeSnapshot(snapshot, CURRENT_FINGERPRINT);

        StructureBlockCatalog catalog = StructureGenCli.loadContentCatalog(
                projectRoot, snapshot.toString(), CURRENT_FINGERPRINT);

        assertInstanceOf(JsonSnapshotStructureBlockCatalog.class, catalog);
    }

    @Test
    void rejectsCatalogFileSymbolicLinkWhereSupported() throws IOException {
        Path projectRoot = tempDirectory.resolve("project-file-link");
        Path catalogPath = catalogPath(projectRoot);
        Path realSnapshot = tempDirectory.resolve("real-catalog.json");
        writeSnapshot(realSnapshot, CURRENT_FINGERPRINT);
        Files.createDirectories(catalogPath.getParent());
        createSymbolicLinkOrSkip(catalogPath, realSnapshot);

        IOException exception = assertThrows(IOException.class, () -> StructureGenCli.loadContentCatalog(
                projectRoot, catalogPath.toString(), CURRENT_FINGERPRINT
        ));
        assertTrue(exception.getMessage().contains("Refusing symbolic-link structuregen catalog snapshot"));
    }

    @Test
    void rejectsCatalogAncestorSymbolicLinkWhereSupported() throws IOException {
        Path projectRoot = tempDirectory.resolve("project-directory-link");
        Path catalogRoot = catalogPath(projectRoot).getParent();
        Path realCatalogRoot = tempDirectory.resolve("real-catalog-directory");
        Files.createDirectories(catalogRoot.getParent());
        Files.createDirectories(realCatalogRoot);
        writeSnapshot(realCatalogRoot.resolve("available-content.json"), CURRENT_FINGERPRINT);
        createSymbolicLinkOrSkip(catalogRoot, realCatalogRoot);

        IOException exception = assertThrows(IOException.class, () -> StructureGenCli.loadContentCatalog(
                projectRoot, catalogRoot.resolve("available-content.json").toString(), CURRENT_FINGERPRINT
        ));
        assertTrue(exception.getMessage().contains("Refusing symbolic-link structuregen catalog snapshot"));
    }

    private Path catalogPath(Path projectRoot) {
        return projectRoot.resolve("build/generated/structuregen/catalog/available-content.json")
                .toAbsolutePath().normalize();
    }

    private void writeSnapshot(Path path, String fingerprint) throws IOException {
        Files.createDirectories(path.getParent());
        Files.writeString(path, """
                {
                  "schemaVersion": 2,
                  "environmentFingerprint": "%s",
                  "mods": [{"id": "example", "version": "1.0.0"}],
                  "blocks": [{
                    "id": "example:panel",
                    "properties": {},
                    "defaultProperties": {}
                  }]
                }
                """.formatted(fingerprint));
    }

    private void createSymbolicLinkOrSkip(Path link, Path target) {
        try {
            Files.createSymbolicLink(link, target);
        } catch (UnsupportedOperationException | IOException | SecurityException exception) {
            Assumptions.assumeTrue(false, "Symbolic links are unavailable: " + exception.getMessage());
        }
    }
}
