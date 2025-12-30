package com.thunder.wildernessodysseyapi.WorldGen.structure;

import com.thunder.wildernessodysseyapi.Core.ModConstants;
import net.neoforged.fml.loading.FMLPaths;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Installs the bundled bunker schematic into the Starter Structure config directory so that
 * worlds default to the Wilderness Odyssey bunker without manual user setup.
 */
public final class StarterStructureBundler {
    private static final String BUNKER_FILE_NAME = "bunker.schem";
    private static final String RESOURCE_PATH = "/assets/wildernessodysseyapi/schematics/" + BUNKER_FILE_NAME;
    private static final Path TARGET_PATH = FMLPaths.CONFIGDIR.get()
            .resolve("starterstructure")
            .resolve("schematics")
            .resolve(BUNKER_FILE_NAME);

    private StarterStructureBundler() {
    }

    public static void ensureBundledBunkerPresent() {
        try (InputStream bunkerStream = StarterStructureBundler.class.getResourceAsStream(RESOURCE_PATH)) {
            if (bunkerStream == null) {
                ModConstants.LOGGER.info("[Starter Structure compat] No bundled bunker schematic found; skipping auto-install. Place your own bunker at {} to use it by default.",
                        TARGET_PATH.toAbsolutePath());
                return;
            }

            Path targetDir = TARGET_PATH.getParent();
            if (targetDir != null) {
                Files.createDirectories(targetDir);
            }

            if (Files.exists(TARGET_PATH)) {
                ModConstants.LOGGER.debug("[Starter Structure compat] Starter bunker already present at {}; leaving user copy untouched.", TARGET_PATH.toAbsolutePath());
                return;
            }

            Files.copy(bunkerStream, TARGET_PATH);
            ModConstants.LOGGER.info("[Starter Structure compat] Installed bundled starter bunker at {}", TARGET_PATH.toAbsolutePath());
        } catch (IOException e) {
            ModConstants.LOGGER.warn("[Starter Structure compat] Failed to install bundled starter bunker to {}.", TARGET_PATH.toAbsolutePath(), e);
        }
    }

    /**
     * Returns the target path where the bundled bunker schematic should live.
     * Exposed for automated tests that need to inspect or paste the bunker.
     */
    public static Path getBundledBunkerPath() {
        return TARGET_PATH;
    }
}
