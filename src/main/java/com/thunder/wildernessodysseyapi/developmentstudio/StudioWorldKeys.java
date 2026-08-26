package com.thunder.wildernessodysseyapi.developmentstudio;

import com.thunder.wildernessodysseyapi.core.ModConstants;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.levelgen.NoiseGeneratorSettings;

/**
 * Registry keys retained for compatibility with existing Development Studio worlds.
 *
 * <p>Older Studio worlds use a key-distinct copy of Minecraft's normal
 * Overworld noise settings. The selectable world preset has been removed, but
 * this key and its data entry must remain so those saves can still load and
 * recover their persisted Studio identity.</p>
 */
public final class StudioWorldKeys {
    public static final ResourceKey<NoiseGeneratorSettings> DEVELOPMENT_STUDIO_NOISE_SETTINGS = ResourceKey.create(
            Registries.NOISE_SETTINGS,
            ResourceLocation.fromNamespaceAndPath(ModConstants.MOD_ID, "development_studio")
    );

    private StudioWorldKeys() {
    }
}
