package com.thunder.wildernessodysseyapi.worldgen.coast;

import com.thunder.wildernessodysseyapi.watersystem.ocean.coast.CoastalWaveProfile;

/** Pure band selection shared by coastal worldgen tests and the surface feature. */
public final class CoastalTerrainProfile {

    private CoastalTerrainProfile() {
    }

    /** Selects a transition band from open water toward the biome interior. */
    public static Zone zone(
            CoastalWaveProfile.ShoreType shoreType,
            int distanceToWaterBlocks
    ) {
        CoastalWaveProfile.ShoreType type = shoreType == null
                ? CoastalWaveProfile.ShoreType.TEMPERATE : shoreType;
        int distance = Math.max(0, distanceToWaterBlocks);
        if (type == CoastalWaveProfile.ShoreType.ROCKY) {
            return distance <= 3 ? Zone.ROCKY_STRAND : Zone.ROCKY_SLOPE;
        }
        if (type == CoastalWaveProfile.ShoreType.GLACIAL) {
            if (distance <= 2) {
                return Zone.ICE_STRAND;
            }
            return distance <= 7 ? Zone.GLACIAL_BEACH : Zone.SNOWFIELD;
        }
        if (type == CoastalWaveProfile.ShoreType.COLD) {
            if (distance <= 3) {
                return Zone.GRAVEL_STRAND;
            }
            return distance <= 8 ? Zone.COLD_BEACH : Zone.SNOWY_MEADOW;
        }
        if (distance <= 2) {
            return Zone.STRANDLINE;
        }
        if (distance <= 6) {
            return Zone.OPEN_BEACH;
        }
        if (distance <= 11) {
            return Zone.DUNE;
        }
        return Zone.COASTAL_MEADOW;
    }

    /** Returns a deterministic quality-independent dune rise for one column. */
    public static int duneRise(
            CoastalWaveProfile.ShoreType shoreType,
            Zone zone,
            double broadNoise,
            int configuredMaximum
    ) {
        if (zone != Zone.DUNE || configuredMaximum <= 0) {
            return 0;
        }
        CoastalWaveProfile.ShoreType type = shoreType == null
                ? CoastalWaveProfile.ShoreType.TEMPERATE : shoreType;
        int profileMaximum = switch (type) {
            case DUNE -> configuredMaximum;
            case TEMPERATE, TROPICAL -> Math.min(1, configuredMaximum);
            case ROCKY, COLD, GLACIAL -> 0;
        };
        if (profileMaximum == 0) {
            return 0;
        }
        double normalized = Math.max(0.0, Math.min(0.999_999, broadNoise * 0.5 + 0.5));
        return Math.min(profileMaximum, (int) Math.floor(normalized * (profileMaximum + 1)));
    }

    /**
     * Selects one sparse persistent detail for a four-by-four coastal cell.
     *
     * <p>The caller supplies stable unit rolls so placement stays deterministic
     * without allocating a random source per column. Returning {@link Detail#NONE}
     * is the common case and keeps authored beaches open rather than cluttered.</p>
     */
    public static Detail detail(
            CoastalWaveProfile.ShoreType shoreType,
            Zone zone,
            double presenceRoll,
            double variantRoll,
            double density
    ) {
        CoastalWaveProfile.ShoreType type = shoreType == null
                ? CoastalWaveProfile.ShoreType.TEMPERATE : shoreType;
        Zone safeZone = zone == null ? Zone.OPEN_BEACH : zone;
        double safeDensity = unit(density);
        double baseChance = switch (safeZone) {
            case STRANDLINE, ROCKY_STRAND, GRAVEL_STRAND, ICE_STRAND -> 0.42;
            case OPEN_BEACH, COLD_BEACH, GLACIAL_BEACH -> 0.32;
            case DUNE, COASTAL_MEADOW, SNOWY_MEADOW -> 0.38;
            case ROCKY_SLOPE -> 0.25;
            case SNOWFIELD -> 0.14;
        };
        if (unit(presenceRoll) >= baseChance * safeDensity) {
            return Detail.NONE;
        }

        double variant = unit(variantRoll);
        return switch (type) {
            case TEMPERATE -> temperateDetail(safeZone, variant);
            case DUNE -> duneDetail(safeZone, variant);
            case ROCKY -> rockyDetail(safeZone, variant);
            case COLD -> coldDetail(safeZone, variant);
            case GLACIAL -> glacialDetail(safeZone, variant);
            case TROPICAL -> tropicalDetail(safeZone, variant);
        };
    }

    private static Detail temperateDetail(Zone zone, double variant) {
        return switch (zone) {
            case STRANDLINE -> variant < 0.16 ? Detail.TIDE_POOL
                    : variant < 0.42 ? Detail.SHELL_PATCH
                    : variant < 0.68 ? Detail.DRIFTWOOD : Detail.ROCK_CLUSTER;
            case OPEN_BEACH -> variant < 0.38 ? Detail.SHELL_PATCH
                    : variant < 0.62 ? Detail.DRIFTWOOD
                    : variant < 0.82 ? Detail.ROCK_CLUSTER : Detail.BEACH_GRASS;
            case DUNE, COASTAL_MEADOW -> variant < 0.76
                    ? Detail.BEACH_GRASS : Detail.DRIFTWOOD;
            default -> Detail.ROCK_CLUSTER;
        };
    }

    private static Detail duneDetail(Zone zone, double variant) {
        return switch (zone) {
            case STRANDLINE -> variant < 0.18 ? Detail.TIDE_POOL
                    : variant < 0.48 ? Detail.SHELL_PATCH : Detail.DRIFTWOOD;
            case OPEN_BEACH -> variant < 0.40 ? Detail.SHELL_PATCH
                    : variant < 0.66 ? Detail.DRIFTWOOD : Detail.BEACH_GRASS;
            case DUNE, COASTAL_MEADOW -> Detail.BEACH_GRASS;
            default -> Detail.DRIFTWOOD;
        };
    }

    private static Detail rockyDetail(Zone zone, double variant) {
        if (zone == Zone.ROCKY_STRAND) {
            if (variant < 0.07) {
                return Detail.SEA_STACK;
            }
            if (variant < 0.27) {
                return Detail.TIDE_POOL;
            }
        }
        return variant < 0.82 ? Detail.ROCK_CLUSTER : Detail.SHELL_PATCH;
    }

    private static Detail coldDetail(Zone zone, double variant) {
        return switch (zone) {
            case GRAVEL_STRAND -> variant < 0.18 ? Detail.TIDE_POOL
                    : variant < 0.46 ? Detail.ICE_FRAGMENT
                    : variant < 0.72 ? Detail.ROCK_CLUSTER : Detail.DRIFTWOOD;
            case COLD_BEACH -> variant < 0.38 ? Detail.ICE_FRAGMENT
                    : variant < 0.72 ? Detail.ROCK_CLUSTER : Detail.DRIFTWOOD;
            case SNOWY_MEADOW -> Detail.BEACH_GRASS;
            default -> Detail.ROCK_CLUSTER;
        };
    }

    private static Detail glacialDetail(Zone zone, double variant) {
        if (zone == Zone.ICE_STRAND && variant < 0.05) {
            return Detail.SEA_STACK;
        }
        if ((zone == Zone.ICE_STRAND || zone == Zone.GLACIAL_BEACH) && variant < 0.18) {
            return Detail.TIDE_POOL;
        }
        return variant < 0.68 ? Detail.ICE_FRAGMENT : Detail.ROCK_CLUSTER;
    }

    private static Detail tropicalDetail(Zone zone, double variant) {
        return switch (zone) {
            case STRANDLINE -> variant < 0.22 ? Detail.TIDE_POOL
                    : variant < 0.62 ? Detail.SHELL_PATCH : Detail.DRIFTWOOD;
            case OPEN_BEACH -> variant < 0.48 ? Detail.SHELL_PATCH
                    : variant < 0.70 ? Detail.DRIFTWOOD : Detail.BEACH_GRASS;
            case DUNE, COASTAL_MEADOW -> Detail.BEACH_GRASS;
            default -> Detail.SHELL_PATCH;
        };
    }

    private static double unit(double value) {
        return Double.isFinite(value) ? Math.max(0.0, Math.min(1.0, value)) : 0.0;
    }

    /** Authored terrain/material band used by the bounded coastal feature. */
    public enum Zone {
        STRANDLINE,
        OPEN_BEACH,
        DUNE,
        COASTAL_MEADOW,
        ROCKY_STRAND,
        ROCKY_SLOPE,
        GRAVEL_STRAND,
        COLD_BEACH,
        SNOWY_MEADOW,
        ICE_STRAND,
        GLACIAL_BEACH,
        SNOWFIELD
    }

    /** Sparse generation-time detail categories implemented with vanilla blocks. */
    public enum Detail {
        NONE,
        BEACH_GRASS,
        DRIFTWOOD,
        SHELL_PATCH,
        ROCK_CLUSTER,
        TIDE_POOL,
        ICE_FRAGMENT,
        SEA_STACK
    }
}
