package com.thunder.wildernessodysseyapi.structuregen.content;

import net.minecraft.data.CachedOutput;
import net.minecraft.data.DataProvider;
import net.neoforged.neoforge.data.event.GatherDataEvent;

import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;

/**
 * Writes the fully loaded NeoForge block registry for later offline StructureGen use.
 *
 * <p>The provider participates only in server data generation. Datagen constructs mods
 * and completes registry events but does not start a server world, making it the supported
 * boundary for discovering third-party blocks without enabling gameplay systems.</p>
 */
public final class StructureBlockCatalogSnapshotProvider implements DataProvider {

    /** Absolute JSON destination supplied by the Gradle data run. */
    public static final String OUTPUT_PROPERTY = "wildernessodysseyapi.structuregen.catalogOutput";

    /** SHA-256 hash of the exact dependency environment supplied by the Gradle data run. */
    public static final String FINGERPRINT_PROPERTY = "wildernessodysseyapi.structuregen.catalogFingerprint";

    private final Path outputPath;
    private final String environmentFingerprint;

    private StructureBlockCatalogSnapshotProvider(Path outputPath, String environmentFingerprint) {
        this.outputPath = outputPath;
        this.environmentFingerprint = environmentFingerprint;
    }

    /**
     * Registers the snapshot provider when server data generation requested an output path.
     *
     * <p>NeoForge 1.21.1 exposes a unified {@link GatherDataEvent}, rather than the later
     * {@code GatherDataEvent.Server} subtype, so the server-side boundary is enforced with
     * {@link GatherDataEvent#includeServer()}.</p>
     */
    public static void onGatherData(GatherDataEvent event) {
        if (!event.includeServer()) {
            return;
        }
        String configuredOutput = System.getProperty(OUTPUT_PROPERTY);
        if (configuredOutput == null || configuredOutput.isBlank()) {
            return;
        }
        String configuredFingerprint = System.getProperty(FINGERPRINT_PROPERTY);
        if (configuredFingerprint == null || configuredFingerprint.isBlank()) {
            throw new IllegalArgumentException("System property '" + FINGERPRINT_PROPERTY
                    + "' is required when '" + OUTPUT_PROPERTY + "' requests a StructureGen catalog snapshot.");
        }
        String environmentFingerprint = JsonSnapshotStructureBlockCatalog.requireEnvironmentFingerprint(
                configuredFingerprint
        );

        Path output = Path.of(configuredOutput).normalize();
        if (!output.isAbsolute()) {
            throw new IllegalArgumentException("System property '" + OUTPUT_PROPERTY
                    + "' must be an absolute path; got " + configuredOutput);
        }
        event.getGenerator().addProvider(
                true,
                new StructureBlockCatalogSnapshotProvider(output, environmentFingerprint)
        );
    }

    /** Captures the registered mod list and block state definitions, then writes stable JSON. */
    @Override
    public CompletableFuture<?> run(CachedOutput output) {
        StructureBlockCatalog catalog = RegistryBackedStructureBlockCatalog.captureLoadedRegistry();
        return DataProvider.saveStable(
                output,
                JsonSnapshotStructureBlockCatalog.toJson(catalog, environmentFingerprint),
                outputPath
        );
    }

    /** Returns the stable data-generator cache identity for this snapshot. */
    @Override
    public String getName() {
        return "StructureGen available-content registry snapshot";
    }
}
