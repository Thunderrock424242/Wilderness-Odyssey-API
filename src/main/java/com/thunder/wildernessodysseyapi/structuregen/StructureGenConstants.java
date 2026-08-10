package com.thunder.wildernessodysseyapi.structuregen;

import net.minecraft.SharedConstants;

/**
 * Shared format, namespace, and safety limits for the offline StructureGen pipeline.
 *
 * <p>The limits apply only to authored blueprints. Existing reference structures may
 * be larger and remain readable because inspection never turns them into trusted
 * generated output.</p>
 */
public final class StructureGenConstants {

    public static final String NAMESPACE = "wildernessodysseyapi";
    public static final int BLUEPRINT_FORMAT_VERSION = 1;
    public static final int MINECRAFT_DATA_VERSION = SharedConstants.WORLD_VERSION;
    public static final int MAX_DIMENSION = 256;
    public static final long MAX_VOLUME = 16_777_216L;
    public static final int MAX_BLOCKS = 2_000_000;
    public static final String PROTECTED_BUNKER_NAME = "bunker";

    private StructureGenConstants() {
    }
}
