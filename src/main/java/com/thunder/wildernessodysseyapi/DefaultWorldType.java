package com.thunder.wildernessodysseyapi;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.client.gui.ConfigurationScreen;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;

/**
 * The type Default world type.
 */
@Mod("wildernessodysseyapi")
public class DefaultWorldType {

    /**
     * The constant MOD_ID.
     */
    public static final String MOD_ID = "wildernessodysseyapi";
    /**
     * The constant LOGGER.
     */
    public static final Logger LOGGER = LogManager.getLogger();

    /**
     * Instantiates a new Default world type.
     *
     * @param modContainer the mod container
     */
    public DefaultWorldType(ModContainer modContainer) {
        try {
            Files.createDirectory(ClientConfig.CONFIG_PATH);
        } catch (FileAlreadyExistsException e) {
            DefaultWorldType.LOGGER.debug("Config directory " + DefaultWorldType.MOD_ID + " already exists. Skip creating.");
        } catch (IOException e) {
            DefaultWorldType.LOGGER.error("Failed to create " + DefaultWorldType.MOD_ID + " config directory", e);
        }

        modContainer.registerConfig(ModConfig.Type.CLIENT, ClientConfig.CLIENT_SPEC, DefaultWorldType.MOD_ID + "/client-config.toml");
        modContainer.registerExtensionPoint(IConfigScreenFactory.class, ConfigurationScreen::new);
    }
}