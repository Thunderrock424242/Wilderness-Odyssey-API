/*package com.thunder.wildernessodysseyapi.BigGlobe;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.dedicated.DedicatedServer;
import net.minecraft.server.packs.repository.Pack;
import net.minecraft.server.packs.repository.Pack.Position;
import net.minecraft.server.packs.repository.PackSource;
import net.minecraft.server.packs.repository.RepositorySource;
import net.minecraft.server.packs.FilePackResources;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.storage.PrimaryLevelData;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.AddPackFindersEvent;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.File;
import java.util.Optional;

public class BigGlobeDatapackManager {
    private static final Logger LOGGER = LogManager.getLogger();

    @SubscribeEvent
    public static void onAddPackFinders(AddPackFindersEvent event) {
        // We only want to add packs for data, not for client resources
        if (event.getPackType() != net.minecraft.server.packs.PackType.DATA) {
            return;
        }

        // Attempt to get the MinecraftServer (in some versions, AddPackFindersEvent can supply it)
        MinecraftServer server = event.getServer();
        if (server == null) {
            // If null, we might be in a client environment with no server context yet.
            return;
        }

        // If it's a dedicated server, we can read server properties and world data
        if (server instanceof DedicatedServer dedicatedServer) {
            // Check if the world is BigGlobe
            if (isBigGlobeWorld(server)) {
                // Create or locate the big_globe_datapacks folder in the server’s root directory
                File datapacksFolder = new File(String.valueOf(dedicatedServer.getServerDirectory()), "big_globe_datapacks");
                if (!datapacksFolder.exists()) {
                    boolean created = datapacksFolder.mkdirs();
                    if (created) {
                        LOGGER.info("Created folder for BigGlobe datapacks: {}", datapacksFolder.getAbsolutePath());
                    } else {
                        LOGGER.warn("Failed to create folder for BigGlobe datapacks: {}", datapacksFolder.getAbsolutePath());
                    }
                } else {
                    LOGGER.info("BigGlobe datapacks folder already exists: {}", datapacksFolder.getAbsolutePath());
                }

                // Now add it as a repository source so MC will load any datapacks in it
                registerDatapackFolder(event, datapacksFolder);
            }
        }
    }

    private static boolean isBigGlobeWorld(MinecraftServer server) {
        // In many modern versions, server.getWorldData() is a PrimaryLevelData on a dedicated server
        if (server.getWorldData() instanceof PrimaryLevelData primaryLevelData) {
            // dimension keys for overworld, nether, etc., might be stored in worldGenSettings
            var dimensions = primaryLevelData.worldGenSettings().dimensions().keySet();
            // Often, the overworld is the "main" dimension
            Optional<ResourceLocation> anyDimension = dimensions;
            if (anyDimension.isPresent()) {
                ResourceLocation dim = anyDimension.get();
                // If for some reason BigGlobe uses the overworld dimension as "bigglobe:bigglobe", you'd see it here.
                if ("bigglobe:bigglobe".equals(dim.toString())) {
                    LOGGER.info("Detected BigGlobe world type from dimension registry!");
                    return true;
                }
            }

            // Alternatively, check level-type in server properties
            // (depends on BigGlobe mod instructions—some versions rely on the server.properties "level-type")
            String levelTypeProp = ((DedicatedServer)server).getProperties().levelType;
            if ("bigglobe:bigglobe".equals(levelTypeProp)) {
                LOGGER.info("Detected BigGlobe world type from server.properties!");
                return true;
            }
        }
        return false;
    }

    private static void registerDatapackFolder(AddPackFindersEvent event, File folder) {
        if (!folder.exists()) {
            LOGGER.warn("Datapack folder does not exist (could not register): {}", folder);
            return;
        }

        RepositorySource repoSource = (infoConsumer, packConstructor) -> {
            // The folder might contain multiple datapacks (subfolders, zips, etc.)
            // For a single folder that contains many datapacks, you generally iterate subdirectories
            // or treat each zip/folder as a separate FilePackResources. However, for simplicity,
            // we can also just treat the entire "big_globe_datapacks" as one root "pack" repository.
            // But to truly handle multiple .zip or subfolder packs, you’d do a small loop and create
            // multiple packs. (Below is the simplest approach to show the concept.)

            // We create a "virtual" pack entry for the entire folder:
            Pack packInfo = packConstructor.create(
                    // A unique internal ID
                    "big_globe_datapacks",
                    // A supplier that loads the folder from disk
                    () -> new FilePackResources("BigGlobeDatapacksFolder", folder, false),
                    // The user-friendly name in /datapack list
                    "BigGlobe Datapacks",
                    // Always enabled by default
                    true,
                    // Position in pack ordering
                    Position.TOP,
                    // Where it's coming from
                    PackSource.BUILT_IN
            );
            infoConsumer.accept(packInfo);
        };

        event.addRepositorySource(repoSource);
        LOGGER.info("Registered big_globe_datapacks folder: {}", folder.getAbsolutePath());
    }
}
*/