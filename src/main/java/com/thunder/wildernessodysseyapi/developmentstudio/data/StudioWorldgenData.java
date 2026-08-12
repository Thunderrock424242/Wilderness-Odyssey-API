package com.thunder.wildernessodysseyapi.developmentstudio.data;

import com.thunder.wildernessodysseyapi.core.ModConstants;
import com.thunder.wildernessodysseyapi.developmentstudio.StudioWorldKeys;
import net.minecraft.core.RegistrySetBuilder;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.worldgen.BootstrapContext;
import net.minecraft.world.level.levelgen.NoiseGeneratorSettings;
import net.neoforged.neoforge.data.event.GatherDataEvent;

import java.util.Set;

/** Generates the Studio marker from Minecraft's exact normal Overworld settings factory. */
public final class StudioWorldgenData {
    private StudioWorldgenData() {
    }

    /** Adds only the server-side noise-settings registry entry during data generation. */
    public static void onGatherData(GatherDataEvent event) {
        if (!event.includeServer()) {
            return;
        }
        RegistrySetBuilder builder = new RegistrySetBuilder()
                .add(Registries.NOISE_SETTINGS, StudioWorldgenData::bootstrapNoiseSettings);
        event.createDatapackRegistryObjects(builder, Set.of(ModConstants.MOD_ID));
    }

    /**
     * Registers a key-distinct value built by vanilla's normal Overworld factory.
     *
     * <p>The key gives Studio a one-time creation marker without changing the
     * real Overworld dimension type, biome source, density functions, surface
     * rules, aquifers, ore veins, or mob-generation behavior.</p>
     */
    public static void bootstrapNoiseSettings(BootstrapContext<NoiseGeneratorSettings> context) {
        context.register(
                StudioWorldKeys.DEVELOPMENT_STUDIO_NOISE_SETTINGS,
                NoiseGeneratorSettings.overworld(context, false, false)
        );
    }
}
