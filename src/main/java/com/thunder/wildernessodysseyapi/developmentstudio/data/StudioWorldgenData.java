package com.thunder.wildernessodysseyapi.developmentstudio.data;

import com.thunder.wildernessodysseyapi.core.ModConstants;
import com.thunder.wildernessodysseyapi.developmentstudio.StudioWorldKeys;
import net.minecraft.core.RegistrySetBuilder;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.worldgen.BootstrapContext;
import net.minecraft.world.level.levelgen.NoiseGeneratorSettings;
import net.neoforged.neoforge.data.event.GatherDataEvent;

import java.util.Set;

/** Generates the legacy Studio marker required to load existing Studio worlds. */
public final class StudioWorldgenData {
    private StudioWorldgenData() {
    }

    /** Adds only the compatibility noise-settings entry during data generation. */
    public static void onGatherData(GatherDataEvent event) {
        if (!event.includeServer()) {
            return;
        }
        RegistrySetBuilder builder = new RegistrySetBuilder()
                .add(Registries.NOISE_SETTINGS, StudioWorldgenData::bootstrapNoiseSettings);
        event.createDatapackRegistryObjects(builder, Set.of(ModConstants.MOD_ID));
    }

    /**
     * Registers the key-distinct value used by the removed Studio world preset.
     *
     * <p>Existing saves may reference this registry key from their serialized
     * Overworld generator. Retaining the normal Overworld-equivalent value is a
     * compatibility requirement, not a path for creating new Studio worlds.</p>
     */
    public static void bootstrapNoiseSettings(BootstrapContext<NoiseGeneratorSettings> context) {
        context.register(
                StudioWorldKeys.DEVELOPMENT_STUDIO_NOISE_SETTINGS,
                NoiseGeneratorSettings.overworld(context, false, false)
        );
    }
}
