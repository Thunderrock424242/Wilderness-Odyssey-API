package com.thunder.wildernessodysseyapi.Core;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import static com.thunder.wildernessodysseyapi.WildernessOdysseyAPIMainModClass.MOD_ID;

public class ModConstants {

    /**
     * The constant VERSION.
     */
    public static final String VERSION = "0.0.3"; // Change this to your mod pack version
    /**
     * The constant LOGGER.
     */
    public static final Logger LOGGER = LogManager.getLogger("wildernessodysseyapi");


    // Optional: common paths
    public static final String STRUCTURE_PATH = MOD_ID + ":structures/";
    public static final String PROCESSOR_LIST = MOD_ID + ":meteor_blend";
}
