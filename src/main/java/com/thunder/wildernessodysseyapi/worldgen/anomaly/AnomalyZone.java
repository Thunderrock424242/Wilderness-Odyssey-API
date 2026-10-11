package com.thunder.wildernessodysseyapi.worldgen.anomaly;

import com.thunder.wildernessodysseyapi.core.ModConstants;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.biome.Biome;

import java.util.Optional;

/** Stable biome identities and restrained base atmosphere strength for anomaly wilderness. */
public enum AnomalyZone {
    FOREST("anomaly_forest", "Anomaly Forest", 0.18),
    PLAINS("anomaly_plains", "Anomaly Plains", 0.12),
    HILLS("anomaly_hills", "Anomaly Hills", 0.36),
    FLOATING_FRINGE("floating_fringe", "Floating Fringe", 0.62),
    RIFT_CORE("rift_core", "Rift Core", 0.86);

    private final String biomePath;
    private final String displayName;
    private final double intensity;

    AnomalyZone(String biomePath, String displayName, double intensity) {
        this.biomePath = biomePath;
        this.displayName = displayName;
        this.intensity = intensity;
    }

    public String biomePath() { return biomePath; }
    public String displayName() { return displayName; }
    public double intensity() { return intensity; }

    public ResourceKey<Biome> biomeKey() {
        return ResourceKey.create(Registries.BIOME, ResourceLocation.fromNamespaceAndPath(ModConstants.MOD_ID, biomePath));
    }

    /** Resolves only authored anomaly biomes; unrelated dimensions and vanilla biomes stay unaffected. */
    public static Optional<AnomalyZone> fromBiome(ResourceKey<Biome> key) {
        if (key == null || !ModConstants.MOD_ID.equals(key.location().getNamespace())) {
            return Optional.empty();
        }
        for (AnomalyZone zone : values()) {
            if (zone.biomePath.equals(key.location().getPath())) {
                return Optional.of(zone);
            }
        }
        return Optional.empty();
    }
}
