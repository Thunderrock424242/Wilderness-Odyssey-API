package com.thunder.wildernessodysseyapi.developmentstudio.campus;

import com.thunder.wildernessodysseyapi.core.ModConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;

/** Shared identity, version, and anchor geometry for the data-driven campus. */
public final class StudioCampusLayout {
    public static final int CURRENT_VERSION = 2;
    public static final int LEGACY_VERSION = 1;
    public static final ResourceLocation TEMPLATE_ID = ResourceLocation.fromNamespaceAndPath(
            ModConstants.MOD_ID,
            "development_studio_campus"
    );
    public static final BlockPos LEVELING_MARKER_OFFSET = new BlockPos(32, 4, 32);
    private static final BlockPos LEGACY_LEVELING_MARKER_OFFSET = new BlockPos(10, 0, 10);

    private StudioCampusLayout() {
    }

    /**
     * Keeps the original hub center and walking surface fixed while expanding a
     * version-one campus equally in every horizontal direction.
     */
    public static BlockPos upgradedOrigin(BlockPos legacyOrigin) {
        BlockPos legacyAnchor = legacyOrigin.offset(LEGACY_LEVELING_MARKER_OFFSET);
        return legacyAnchor.subtract(LEVELING_MARKER_OFFSET);
    }
}
