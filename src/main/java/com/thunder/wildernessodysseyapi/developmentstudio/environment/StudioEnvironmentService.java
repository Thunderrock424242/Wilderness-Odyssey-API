package com.thunder.wildernessodysseyapi.developmentstudio.environment;

import com.thunder.wildernessodysseyapi.developmentstudio.StudioServerService;
import com.thunder.wildernessodysseyapi.developmentstudio.inspection.StudioInspection;
import com.thunder.wildernessodysseyapi.developmentstudio.network.StudioEnvironmentActionPayload;
import com.thunder.wildernessodysseyapi.weather.api.PrecipitationType;
import com.thunder.wildernessodysseyapi.weather.simulation.WeatherAuthority;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

/** Server-authoritative dispatcher for Phase 3 inspection and bounded weather experiments. */
public final class StudioEnvironmentService {
    private StudioEnvironmentService() {
    }

    /** Rechecks Studio access, invokes real subsystem owners, and returns a fresh inspection. */
    public static void handle(ServerPlayer player, StudioEnvironmentActionPayload payload) {
        if (!StudioServerService.authorize(player)) {
            return;
        }
        switch (payload.action()) {
            case WEATHER_CLEAR -> weatherResult(player, "cleared",
                    WeatherAuthority.get().clearLocalWeather(player.serverLevel(), player.blockPosition()));
            case WEATHER_RAIN -> weatherResult(player, "set to rain in",
                    WeatherAuthority.get().forcePrecipitation(
                            player.serverLevel(), player.blockPosition(), PrecipitationType.RAIN));
            case WEATHER_SNOW -> weatherResult(player, "set to snow in",
                    WeatherAuthority.get().forcePrecipitation(
                            player.serverLevel(), player.blockPosition(), PrecipitationType.SNOW));
            case WEATHER_HAIL -> weatherResult(player, "set to hail in",
                    WeatherAuthority.get().forcePrecipitation(
                            player.serverLevel(), player.blockPosition(), PrecipitationType.HAIL));
            default -> {
            }
        }

        StudioInspection inspection = switch (payload.action().modulePath()) {
            case "water" -> StudioEnvironmentInspectionService.water(player);
            case "ecosystem" -> StudioEnvironmentInspectionService.ecosystem(player);
            case "weather" -> StudioEnvironmentInspectionService.weather(player);
            case "worldgen" -> StudioEnvironmentInspectionService.worldgen(player);
            default -> null;
        };
        StudioServerService.openModule(player, payload.action().modulePath(), inspection);
    }

    private static void weatherResult(ServerPlayer player, String action, int changedCells) {
        player.displayClientMessage(Component.literal(
                "Weather authority " + action + " " + changedCells + " local atmosphere cells."
        ), false);
    }
}
