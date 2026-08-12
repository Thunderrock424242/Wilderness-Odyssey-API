package com.thunder.wildernessodysseyapi.developmentstudio;

import com.thunder.wildernessodysseyapi.core.ModConstants;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.levelgen.NoiseGeneratorSettings;
import net.minecraft.world.level.levelgen.presets.WorldPreset;

/**
 * Registry keys that identify the data-driven Development Studio world preset.
 *
 * <p>The custom noise-settings holder is generated from Minecraft's own normal
 * Overworld factory and acts only as a creation marker. Keeping the real
 * {@code minecraft:overworld} dimension type preserves compatibility with
 * worldgen integrations such as Biolith.</p>
 */
public final class StudioWorldKeys {
    public static final ResourceKey<WorldPreset> DEVELOPMENT_STUDIO_PRESET = ResourceKey.create(
            Registries.WORLD_PRESET,
            ResourceLocation.fromNamespaceAndPath(ModConstants.MOD_ID, "development_studio")
    );
    public static final ResourceKey<NoiseGeneratorSettings> DEVELOPMENT_STUDIO_NOISE_SETTINGS = ResourceKey.create(
            Registries.NOISE_SETTINGS,
            ResourceLocation.fromNamespaceAndPath(ModConstants.MOD_ID, "development_studio")
    );

    private StudioWorldKeys() {
    }
}
