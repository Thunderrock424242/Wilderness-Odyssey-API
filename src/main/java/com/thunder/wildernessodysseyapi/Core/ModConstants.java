package com.thunder.wildernessodysseyapi.Core;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/****
 * ModConstants for the Wilderness Odyssey API mod.
 */
public class ModConstants {

    /**
     * The constant MOD_ID.
     */
    public static final String MOD_ID = "wildernessodysseyapi";

    /**
     * this shows the world version MINOR means its small change MAJOR is a game breaking change
     * The default world version for the mod; update this when releasing new versions
     */
    public static final String MOD_DEFAULT_WORLD_VERSION = "1.0.0"; // Update your Mod Version Here Major , Minor , Patch

    /**
     * The constant VERSION.
     */
    public static final String VERSION = "0.0.3"; // Change this to your mod pack version
    /**
     * The constant LOGGER.
     */
    public static final Logger LOGGER = LogManager.getLogger("wildernessodysseyapi");
}
