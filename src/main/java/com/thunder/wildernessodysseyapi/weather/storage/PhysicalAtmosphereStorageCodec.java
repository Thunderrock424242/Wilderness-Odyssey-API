package com.thunder.wildernessodysseyapi.weather.storage;

import com.thunder.wildernessodysseyapi.weather.api.AtmosphereView;
import com.thunder.wildernessodysseyapi.weather.api.SurfaceWeatherState;
import com.thunder.wildernessodysseyapi.weather.simulation.*;
import net.minecraft.nbt.CompoundTag;

/** Exact-double physical continuity alongside the existing quantized compatibility words. */
public final class PhysicalAtmosphereStorageCodec {
    private PhysicalAtmosphereStorageCodec() { }

    public static CompoundTag encode(AtmosphereView view) {
        AtmosphericPhysicalState s = view.physicalState();
        CompoundTag tag = new CompoundTag();
        tag.putLongArray("state", bits(s.temperatureCelsius(), s.pressureHpa(), s.vaporKgPerSquareMetre(),
                s.cloudLiquidKgPerSquareMetre(), s.cloudIceKgPerSquareMetre(), s.verticalVelocityMetresPerSecond(),
                s.instabilityJoulesPerKg(), s.stormEnergy(), s.surfaceTemperatureCelsius(), s.precipitationMmPerHour(),
                s.surface().wetness(), s.surface().puddleCoverage(), s.surface().snowpack(), s.surface().frozenFraction()));
        long[] layers = new long[24];
        for (int i = 0; i < 4; i++) {
            AtmosphericLayer l = s.column().layer(i);
            System.arraycopy(bits(l.heightMetres(), l.pressureHpa(), l.temperatureCelsius(), l.relativeHumidity(),
                    l.windXMetresPerSecond(), l.windZMetresPerSecond()), 0, layers, i * 6, 6);
        }
        tag.putLongArray("layers", layers);
        AtmosphereEnvironment e = view.environment();
        tag.putDouble("seasonalHumidityOffset", e.seasonalHumidityOffset());
        tag.putLongArray("environment", bits(e.biomeTemperatureCelsius(), e.biomeHumidity(), e.elevationBlocks(),
                e.waterCoverage(), e.daylight(), e.dimensionTemperatureOffset(), e.seasonalTemperatureOffset(),
                e.atmosphericVariation(), e.seasonalStorminessOffset(), e.seasonalEvaporationMultiplier(),
                e.terrainGradientX(), e.terrainGradientZ(), e.terrainRoughness(), e.oceanCoverage(),
                e.inlandWaterCoverage(), e.fireSeasonFactor(), e.snowSeasonFactor(),
                e.seasonCalendarAvailable() ? 1 : 0, e.seasonalCyclePhase()));
        return tag;
    }

    public static AtmosphericPhysicalState state(CompoundTag tag) {
        double[] s = values(tag, "state", 14, false);
        double[] layers = values(tag, "layers", 24, false);
        return new AtmosphericPhysicalState(s[0], s[1], s[2], s[3], s[4],
                new AtmosphericColumn(layer(layers, 0), layer(layers, 6), layer(layers, 12), layer(layers, 18)),
                s[5], s[6], s[7], s[8], s[9], new SurfaceWeatherState(s[10], s[11], s[12], s[13]));
    }

    public static AtmosphereEnvironment environment(CompoundTag tag) {
        double[] e = values(tag, "environment", 19, true);
        double humidityOffset = tag.getDouble("seasonalHumidityOffset");
        if (!Double.isFinite(humidityOffset)) throw new IllegalArgumentException("Nonfinite seasonal humidity offset");
        return new AtmosphereEnvironment(e[0], e[1], e[2], e[3], e[4], e[5], e[6], e[7], e[8], e[9],
                e[10], e[11], e[12], e[13], e[14], e[15], e[16], e[17] > .5, e[18], humidityOffset);
    }

    private static AtmosphericLayer layer(double[] values, int i) {
        return new AtmosphericLayer(values[i], values[i + 1], values[i + 2], values[i + 3], values[i + 4], values[i + 5]);
    }

    private static long[] bits(double... values) {
        long[] encoded = new long[values.length];
        for (int i = 0; i < values.length; i++) encoded[i] = Double.doubleToLongBits(values[i]);
        return encoded;
    }

    private static double[] values(CompoundTag tag, String name, int count, boolean allowLastNaN) {
        long[] encoded = tag.getLongArray(name);
        if (encoded.length != count) throw new IllegalArgumentException("Malformed physical atmosphere " + name);
        double[] values = new double[count];
        for (int i = 0; i < count; i++) {
            values[i] = Double.longBitsToDouble(encoded[i]);
            if (!Double.isFinite(values[i]) && !(allowLastNaN && i == count - 1 && Double.isNaN(values[i]))) {
                throw new IllegalArgumentException("Nonfinite physical atmosphere " + name);
            }
        }
        return values;
    }
}
