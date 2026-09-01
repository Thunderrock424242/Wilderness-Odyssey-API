package com.thunder.wildernessodysseyapi.watersystem.water.render;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.thunder.wildernessodysseyapi.core.ModConstants;
import com.thunder.wildernessodysseyapi.watersystem.ocean.coast.CoastalWaveProfile;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;

import java.io.IOException;
import java.io.Reader;
import java.util.EnumMap;
import java.util.Map;

/**
 * Client resource-pack boundary for per-shore coastal wave tuning.
 *
 * <p>Malformed or missing data fails back to immutable code defaults. Reloads
 * replace the map atomically and invalidate only the loaded shoreline cache;
 * no gameplay state, chunks, or server authority are touched.</p>
 */
public final class ClientCoastalWaveProfiles implements ResourceManagerReloadListener {

    private static final ResourceLocation RESOURCE = ResourceLocation.fromNamespaceAndPath(
            ModConstants.MOD_ID, "coastal_wave_profiles.json");
    private static volatile Map<CoastalWaveProfile.ShoreType, CoastalWaveProfile> profiles =
            defaults();

    /** Returns the active resource-pack profile for one classified shore. */
    public static CoastalWaveProfile profile(CoastalWaveProfile.ShoreType shoreType) {
        CoastalWaveProfile.ShoreType type = shoreType == null
                ? CoastalWaveProfile.ShoreType.TEMPERATE : shoreType;
        return profiles.getOrDefault(type, CoastalWaveProfile.forType(type));
    }

    @Override
    public void onResourceManagerReload(ResourceManager resourceManager) {
        Map<CoastalWaveProfile.ShoreType, CoastalWaveProfile> loaded = defaults();
        try {
            var resource = resourceManager.getResource(RESOURCE);
            if (resource.isPresent()) {
                try (Reader reader = resource.get().openAsReader()) {
                    JsonObject root = JsonParser.parseReader(reader).getAsJsonObject();
                    EnumMap<CoastalWaveProfile.ShoreType, CoastalWaveProfile> decoded =
                            new EnumMap<>(CoastalWaveProfile.ShoreType.class);
                    for (CoastalWaveProfile.ShoreType type
                            : CoastalWaveProfile.ShoreType.values()) {
                        CoastalWaveProfile fallback = CoastalWaveProfile.forType(type);
                        String key = type.name().toLowerCase(java.util.Locale.ROOT);
                        JsonObject object = root.has(key) && root.get(key).isJsonObject()
                                ? root.getAsJsonObject(key) : null;
                        decoded.put(type, object == null ? fallback : decode(type, object, fallback));
                    }
                    loaded = Map.copyOf(decoded);
                }
            }
        } catch (IOException | RuntimeException exception) {
            ModConstants.LOGGER.warn(
                    "Unable to reload coastal wave profiles; using built-in safe defaults",
                    exception
            );
            loaded = defaults();
        }
        profiles = loaded;
        ClientCoastalSegmentStore.clear();
    }

    private static CoastalWaveProfile decode(
            CoastalWaveProfile.ShoreType type,
            JsonObject object,
            CoastalWaveProfile fallback
    ) {
        return new CoastalWaveProfile(
                type,
                number(object, "waveHeightMultiplier", fallback.waveHeightMultiplier()),
                number(object, "waveFrequencyMultiplier", fallback.waveFrequencyMultiplier()),
                number(object, "breakerDistanceBlocks", fallback.breakerDistanceBlocks()),
                number(object, "breakerStrength", fallback.breakerStrength()),
                number(object, "runUpDistanceBlocks", fallback.runUpDistanceBlocks()),
                number(object, "retreatSpeed", fallback.retreatSpeed()),
                number(object, "foamAmount", fallback.foamAmount()),
                number(object, "crashSoundVolume", fallback.crashSoundVolume()),
                number(object, "crashSoundRadiusBlocks", fallback.crashSoundRadiusBlocks()),
                number(object, "turbulence", fallback.turbulence()),
                integer(object, "shorelineWetnessDurationTicks",
                        fallback.shorelineWetnessDurationTicks())
        );
    }

    private static float number(JsonObject object, String name, float fallback) {
        if (!object.has(name) || !object.get(name).isJsonPrimitive()) {
            return fallback;
        }
        try {
            return object.get(name).getAsFloat();
        } catch (RuntimeException exception) {
            return fallback;
        }
    }

    private static int integer(JsonObject object, String name, int fallback) {
        if (!object.has(name) || !object.get(name).isJsonPrimitive()) {
            return fallback;
        }
        try {
            return object.get(name).getAsInt();
        } catch (RuntimeException exception) {
            return fallback;
        }
    }

    private static Map<CoastalWaveProfile.ShoreType, CoastalWaveProfile> defaults() {
        EnumMap<CoastalWaveProfile.ShoreType, CoastalWaveProfile> defaults =
                new EnumMap<>(CoastalWaveProfile.ShoreType.class);
        for (CoastalWaveProfile.ShoreType type : CoastalWaveProfile.ShoreType.values()) {
            defaults.put(type, CoastalWaveProfile.forType(type));
        }
        return Map.copyOf(defaults);
    }
}
