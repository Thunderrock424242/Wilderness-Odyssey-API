package com.thunder.wildernessodysseyapi.Core;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class ModConstants {

    /**
     * The constant MOD_ID.
     */
    public static final String MOD_ID = "wildernessodysseyapi";

    /**
     * The constant VERSION.
     */
    public static final String VERSION = "0.0.3"; // Change this to your mod pack version
    /**
     * The constant LOGGER.
     */
    public static final Logger LOGGER = LogManager.getLogger("wildernessodysseyapi");

    /**
     * this shows the world version MINOR means its small change MAJOR is a game breaking change
     */
    public static final String CURRENT_WORLD_VERSION = "1.1";
    /** Numeric parts for comparison logic */
    public static final int CURRENT_WORLD_VERSION_MAJOR = 1;
    public static final int CURRENT_WORLD_VERSION_MINOR = 1;


    // Optional: common paths
    public static final String STRUCTURE_PATH = MOD_ID + ":structures/";
    public static final String PROCESSOR_LIST = MOD_ID + ":meteor_blend";
}
