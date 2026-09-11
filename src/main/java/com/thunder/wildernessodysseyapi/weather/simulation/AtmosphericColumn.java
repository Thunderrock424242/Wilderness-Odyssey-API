package com.thunder.wildernessodysseyapi.weather.simulation;

import com.thunder.wildernessodysseyapi.weather.api.WindVector;
import java.util.Objects;

/** Four retained layer samples, avoiding a volumetric grid or any world access. */
public record AtmosphericColumn(AtmosphericLayer surface, AtmosphericLayer low,
                                AtmosphericLayer middle, AtmosphericLayer upper) {
    public AtmosphericColumn {
        Objects.requireNonNull(surface);
        Objects.requireNonNull(low);
        Objects.requireNonNull(middle);
        Objects.requireNonNull(upper);
        if (surface.heightMetres() != 0.0 || low.heightMetres() <= 0.0
                || middle.heightMetres() <= low.heightMetres() || upper.heightMetres() <= middle.heightMetres()) {
            throw new IllegalArgumentException("Atmospheric layers must ascend from local surface");
        }
    }

    /** Initializes a hydrostatic lapse-rate column from legacy data once during migration. */
    public static AtmosphericColumn initial(double temperature, double pressure, double humidity,
                                             WindVector surfaceWind, WindVector upperWind) {
        return new AtmosphericColumn(layer(0, temperature, pressure, humidity, surfaceWind),
                layer(1500, temperature - 9.75, pressure, humidity, WindVector.lerp(surfaceWind, upperWind, 0.4)),
                layer(3000, temperature - 19.5, pressure, humidity * 0.9, WindVector.lerp(surfaceWind, upperWind, 0.7)),
                layer(5500, temperature - 35.75, pressure, humidity * 0.8, upperWind));
    }

    private static AtmosphericLayer layer(double height, double temperature, double pressure,
                                          double humidity, WindVector wind) {
        return new AtmosphericLayer(height, pressure * Math.exp(-height / 8400.0), temperature,
                humidity, wind.x(), wind.z());
    }

    /** Mass-weighted lower/middle tropospheric steering, in m/s (blocks/s). */
    public WindVector steeringWind() {
        return new WindVector(low.windXMetresPerSecond() * 0.4 + middle.windXMetresPerSecond() * 0.4
                + upper.windXMetresPerSecond() * 0.2,
                low.windZMetresPerSecond() * 0.4 + middle.windZMetresPerSecond() * 0.4
                        + upper.windZMetresPerSecond() * 0.2);
    }

    public double shearMetresPerSecond() {
        return Math.hypot(upper.windXMetresPerSecond() - surface.windXMetresPerSecond(),
                upper.windZMetresPerSecond() - surface.windZMetresPerSecond());
    }

    /** Parcel/ambient thermal contrast integrated over three slabs, a CAPE proxy in J/kg. */
    public double instabilityJoulesPerKg() {
        double total = 0.0;
        AtmosphericLayer previous = surface;
        for (int i = 1; i < 4; i++) {
            AtmosphericLayer layer = layer(i);
            double parcelTemperature = surface.temperatureCelsius() - layer.heightMetres() * 0.006;
            total += Math.max(0.0, parcelTemperature - layer.temperatureCelsius())
                    / (layer.temperatureCelsius() + 273.15) * 9.81
                    * (layer.heightMetres() - previous.heightMetres());
            previous = layer;
        }
        return AtmosphericUnits.clamp(total * surface.relativeHumidity(), 0.0, 6000.0);
    }

    public AtmosphericLayer layer(int index) {
        return switch (index) {
            case 0 -> surface;
            case 1 -> low;
            case 2 -> middle;
            case 3 -> upper;
            default -> throw new IndexOutOfBoundsException(index);
        };
    }

    /** Linear interpolation preserves warm noses and shallow subfreezing surface layers. */
    public double temperatureAt(double height) {
        for (int i = 1; i < 4; i++) {
            AtmosphericLayer above = layer(i);
            AtmosphericLayer below = layer(i - 1);
            if (height <= above.heightMetres()) {
                double t = AtmosphericUnits.unit((height - below.heightMetres())
                        / (above.heightMetres() - below.heightMetres()));
                return below.temperatureCelsius() + t * (above.temperatureCelsius() - below.temperatureCelsius());
            }
        }
        return upper.temperatureCelsius();
    }
}
