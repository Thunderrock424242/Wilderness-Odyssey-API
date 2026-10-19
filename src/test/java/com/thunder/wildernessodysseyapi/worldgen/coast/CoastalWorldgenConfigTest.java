package com.thunder.wildernessodysseyapi.worldgen.coast;

import com.thunder.wildernessodysseyapi.config.WildernessConfigSpecs;
import com.thunder.wildernessodysseyapi.worldgen.coast.config.CoastalWorldgenConfig;
import org.junit.jupiter.api.Test;
import net.neoforged.neoforge.common.ModConfigSpec;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CoastalWorldgenConfigTest {
    @Test
    void beachesDefaultToEnabledWhileDensityDefaultsRemainUnchanged() {
        var spec = WildernessConfigSpecs.commonSpec();
        assertEquals(true, spec.getSpec().<ModConfigSpec.ValueSpec>get(
                List.of("coastalWorldgen", "enableBeachBiomeFamily")).getDefault());
        assertEquals(0.55, spec.getSpec().<ModConfigSpec.ValueSpec>get(
                List.of("coastalWorldgen", "coastalDetailDensity")).getDefault());
        for (String name : List.of("palmDensity", "driftwoodDensity", "rockClusterDensity")) {
            assertEquals(1.0, spec.getSpec().<ModConfigSpec.ValueSpec>get(
                    List.of("coastalWorldgen", name)).getDefault());
        }
        assertEquals(List.of("coastalWorldgen", "palmDensity"), CoastalWorldgenConfig.PALM_DENSITY.getPath());
    }
}
