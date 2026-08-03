package com.thunder.wildernessodysseyapi.weather.wildfire;

import com.thunder.wildernessodysseyapi.core.ModConstants;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.block.Block;

/** Datapack extension points for natural wildfire ignition targets. */
public final class WildfireTags {

    /** Exposed natural fuels that a campfire ember may directly ignite. */
    public static final TagKey<Block> IGNITION_FUELS = TagKey.create(
            Registries.BLOCK,
            ResourceLocation.fromNamespaceAndPath(ModConstants.MOD_ID, "wildfire_ignition_fuels")
    );

    private WildfireTags() {
    }
}
