package com.thunder.wildernessodysseyapi.developmentstudio.campus;

import com.thunder.wildernessodysseyapi.core.ModConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;

/** Shared template identity and marker position for the Phase 1 campus. */
public final class StudioCampusLayout {
    public static final ResourceLocation TEMPLATE_ID = ResourceLocation.fromNamespaceAndPath(
            ModConstants.MOD_ID,
            "development_studio_campus"
    );
    public static final BlockPos LEVELING_MARKER_OFFSET = new BlockPos(10, 0, 10);

    private StudioCampusLayout() {
    }
}
