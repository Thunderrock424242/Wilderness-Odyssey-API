package com.thunder.wildernessodysseyapi.worldgen.biome;

import com.thunder.wildernessodysseyapi.core.ModConstants;
import com.thunder.wildernessodysseyapi.watersystem.ocean.coast.CoastalWaveProfile;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.biome.Biome;

import java.util.Optional;

public final class ModBiomes {
    private ModBiomes() {
    }

    public static final ResourceKey<Biome> ANOMALY_FOREST_KEY = ResourceKey.create(Registries.BIOME,
            ResourceLocation.fromNamespaceAndPath(ModConstants.MOD_ID, "anomaly_forest"));
    public static final ResourceKey<Biome> POLAR_ICE_SHEET_KEY = key("polar_ice_sheet");
    public static final ResourceKey<Biome> GLACIAL_HIGHLANDS_KEY = key("glacial_highlands");
    public static final ResourceKey<Biome> POLAR_GLACIAL_BASIN_KEY = key("polar_glacial_basin");
    public static final ResourceKey<Biome> GLACIAL_MELTWATER_VALLEY_KEY = key("glacial_meltwater_valley");
    public static final ResourceKey<Biome> ICEBERG_COAST_KEY = key("iceberg_coast");
    public static final ResourceKey<Biome> TEMPERATE_BEACH_KEY = key("temperate_beach");
    public static final ResourceKey<Biome> DUNE_BEACH_KEY = key("dune_beach");
    public static final ResourceKey<Biome> ROCKY_COAST_KEY = key("rocky_coast");
    public static final ResourceKey<Biome> COLD_BEACH_KEY = key("cold_beach");
    public static final ResourceKey<Biome> GLACIAL_BEACH_KEY = key("glacial_beach");
    public static final ResourceKey<Biome> TROPICAL_BEACH_KEY = key("tropical_beach");

    /** Resolves an authored coastal biome to the same profile used by client waves. */
    public static Optional<CoastalWaveProfile.ShoreType> coastalShoreType(Holder<Biome> biome) {
        if (biome == null) {
            return Optional.empty();
        }
        return biome.unwrapKey().flatMap(ModBiomes::coastalShoreType);
    }

    /** Resolves an authored coastal biome key without relying on display climate. */
    public static Optional<CoastalWaveProfile.ShoreType> coastalShoreType(ResourceKey<Biome> key) {
        if (TEMPERATE_BEACH_KEY.equals(key)) {
            return Optional.of(CoastalWaveProfile.ShoreType.TEMPERATE);
        }
        if (DUNE_BEACH_KEY.equals(key)) {
            return Optional.of(CoastalWaveProfile.ShoreType.DUNE);
        }
        if (ROCKY_COAST_KEY.equals(key)) {
            return Optional.of(CoastalWaveProfile.ShoreType.ROCKY);
        }
        if (COLD_BEACH_KEY.equals(key)) {
            return Optional.of(CoastalWaveProfile.ShoreType.COLD);
        }
        if (GLACIAL_BEACH_KEY.equals(key)) {
            return Optional.of(CoastalWaveProfile.ShoreType.GLACIAL);
        }
        if (TROPICAL_BEACH_KEY.equals(key)) {
            return Optional.of(CoastalWaveProfile.ShoreType.TROPICAL);
        }
        return Optional.empty();
    }

    private static ResourceKey<Biome> key(String path) {
        return ResourceKey.create(
                Registries.BIOME,
                ResourceLocation.fromNamespaceAndPath(ModConstants.MOD_ID, path)
        );
    }
}
