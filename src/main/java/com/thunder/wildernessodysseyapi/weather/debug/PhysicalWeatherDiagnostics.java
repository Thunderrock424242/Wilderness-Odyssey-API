package com.thunder.wildernessodysseyapi.weather.debug;

import com.thunder.wildernessodysseyapi.weather.api.AtmosphereView;
import com.thunder.wildernessodysseyapi.weather.simulation.AtmosphericLayer;
import com.thunder.wildernessodysseyapi.weather.simulation.AtmosphericThermodynamics;
import com.thunder.wildernessodysseyapi.weather.simulation.CloudProperties;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** On-demand physical diagnostics; no world reads or additional simulation owner. */
public final class PhysicalWeatherDiagnostics {
    private PhysicalWeatherDiagnostics() { }

    /** Formats the retained cell and its four coarse layers in physical units. */
    public static List<String> describe(AtmosphereView view) {
        var state = view.physicalState();
        CloudProperties cloud = CloudProperties.derive(state);
        List<String> lines = new ArrayList<>();
        lines.add(String.format(Locale.ROOT,
                "Physical cell %d,%d at tick %d: air %.2f C, skin %.2f C, dew point %.2f C, wet bulb %.2f C, RH %.1f%%, pressure %.2f hPa.",
                view.key().x(), view.key().z(), view.lastSimulatedTick(), state.temperatureCelsius(),
                state.surfaceTemperatureCelsius(), AtmosphericThermodynamics.dewPointTemperature(
                        state.temperatureCelsius(), state.relativeHumidity()), state.column().surface().wetBulbCelsius(),
                state.relativeHumidity() * 100, state.pressureHpa()));
        lines.add(String.format(Locale.ROOT,
                "Water: vapor %.6f, liquid %.6f, ice %.6f kg/m2; %s %.4f mm/hour. Cloud base/top %.0f/%.0f m above terrain, lift %+.3f m/s, instability %.1f J/kg, genus %s.",
                state.vaporKgPerSquareMetre(), state.cloudLiquidKgPerSquareMetre(), state.cloudIceKgPerSquareMetre(),
                view.sample().precipitationType(), state.precipitationMmPerHour(), cloud.baseMetres(), cloud.topMetres(),
                state.verticalVelocityMetresPerSecond(), state.instabilityJoulesPerKg(),
                com.thunder.wildernessodysseyapi.weather.api.CloudTypeClassifier.classify(state)));
        for (int i = 0; i < 4; i++) {
            AtmosphericLayer layer = state.column().layer(i);
            lines.add(String.format(Locale.ROOT,
                    "Layer %d: %.0f m, %.1f hPa, %.2f C, RH %.1f%%, wind east/south %+.2f/%+.2f m/s (%.2f m/s).",
                    i, layer.heightMetres(), layer.pressureHpa(), layer.temperatureCelsius(),
                    layer.relativeHumidity() * 100, layer.windXMetresPerSecond(), layer.windZMetresPerSecond(),
                    layer.windSpeedMetresPerSecond()));
        }
        return List.copyOf(lines);
    }
}
