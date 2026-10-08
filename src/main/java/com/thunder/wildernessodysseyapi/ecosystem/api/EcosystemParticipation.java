package com.thunder.wildernessodysseyapi.ecosystem.api;

import com.thunder.wildernessodysseyapi.ecosystem.config.EcosystemConfig;
import com.thunder.wildernessodysseyapi.environment.api.EnvironmentDimensionProfile;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;

/** Shared server policy for ecosystem owners, controllers, and external API callers. */
public final class EcosystemParticipation {
    private EcosystemParticipation() {
    }

    /** Requires both the server toggle and this dimension's living-ecosystem channel. */
    public static boolean isEnabled(ServerLevel level) {
        return isEnabled(level.dimension(), EcosystemConfig.ENABLED.get());
    }

    /** Applies the dimension policy to a captured server enablement value. */
    public static boolean isEnabled(ResourceKey<Level> dimension, boolean enabled) {
        return enabled && EnvironmentDimensionProfile.forDimension(dimension).ecosystem();
    }
}
