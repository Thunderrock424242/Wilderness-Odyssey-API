package com.thunder.wildernessodysseyapi.watersystem.water.hydrology;

import com.thunder.wildernessodysseyapi.core.ModConstants;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.block.Block;

/**
 * Data-pack extension points for conservative flood placement safety.
 *
 * <p>Air is always the preferred overflow target. Pack authors may opt simple
 * replaceable vegetation into {@link #FLOOD_REPLACEABLE}; protected entries
 * always win. Block entities and structure bounding boxes are rejected even if
 * a block is accidentally placed in the replaceable tag.</p>
 */
public final class WatershedTags {

    /** Replaceable non-air blocks that temporary overflow may occupy. */
    public static final TagKey<Block> FLOOD_REPLACEABLE = blockTag("watershed_flood_replaceable");
    /** Blocks that temporary overflow must never replace. */
    public static final TagKey<Block> FLOOD_PROTECTED = blockTag("watershed_flood_protected");

    private WatershedTags() {
    }

    private static TagKey<Block> blockTag(String path) {
        return TagKey.create(
                Registries.BLOCK,
                ResourceLocation.fromNamespaceAndPath(ModConstants.MOD_ID, path)
        );
    }
}
