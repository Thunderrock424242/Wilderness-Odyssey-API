package com.thunder.wildernessodysseyapi.weather.client;

import com.thunder.wildernessodysseyapi.core.ModConstants;
import com.thunder.wildernessodysseyapi.weather.api.WeatherSample;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.material.FogType;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ViewportEvent;
import net.neoforged.neoforge.event.level.LevelEvent;

import java.util.List;
import java.util.Locale;

/**
 * Connects immutable client weather state to lifecycle, fog, and F3 diagnostics.
 *
 * <p>Only air-camera fog is modified. Water, lava, and powder-snow fog retain
 * their owning vanilla or Wilderness render paths.</p>
 */
@EventBusSubscriber(modid = ModConstants.MOD_ID, value = Dist.CLIENT)
public final class WeatherClientEvents {

    private static final float FOG_RED = 0.62F;
    private static final float FOG_GREEN = 0.67F;
    private static final float FOG_BLUE = 0.72F;

    private WeatherClientEvents() {
    }

    /** Clears stale sequence watermarks before the next server starts syncing weather. */
    @SubscribeEvent
    public static void onLogin(ClientPlayerNetworkEvent.LoggingIn event) {
        ClientWeatherCoordinator.clearAll();
    }

    /** Releases all immutable atmosphere state when the client disconnects. */
    @SubscribeEvent
    public static void onLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        ClientWeatherCoordinator.clearAll();
    }

    /** Clears the displayed region immediately during a dimension unload. */
    @SubscribeEvent
    public static void onLevelUnload(LevelEvent.Unload event) {
        if (event.getLevel() instanceof ClientLevel clientLevel) {
            ClientWeatherCoordinator.clearLevel(clientLevel);
        }
    }

    /** Blends humid precipitation haze into vanilla's air fog color. */
    @SubscribeEvent
    public static void onFogColor(ViewportEvent.ComputeFogColor event) {
        if (event.getCamera().getFluidInCamera() != FogType.NONE) {
            return;
        }
        ClientLevel level = Minecraft.getInstance().level;
        if (!ClientWeatherCoordinator.controls(level)) {
            return;
        }

        WeatherSample sample = ClientWeatherCoordinator.sampleAt(
                level,
                event.getCamera().getPosition()
        );
        float fog = ClientWeatherCoordinator.fogContribution(sample);
        if (fog <= 0.001F) {
            return;
        }

        float thunder = ClientWeatherCoordinator.thunderContribution(sample);
        float colorAmount = fog * 0.5F;
        float stormScale = 1.0F - thunder * 0.35F;
        event.setRed(mix(event.getRed(), FOG_RED * stormScale, colorAmount));
        event.setGreen(mix(event.getGreen(), FOG_GREEN * stormScale, colorAmount));
        event.setBlue(mix(event.getBlue(), FOG_BLUE * stormScale, colorAmount));
    }

    /** Shortens only the air-fog far plane according to the local weather sample. */
    @SubscribeEvent
    public static void onRenderFog(ViewportEvent.RenderFog event) {
        if (event.getType() != FogType.NONE) {
            return;
        }
        ClientLevel level = Minecraft.getInstance().level;
        if (!ClientWeatherCoordinator.controls(level)) {
            return;
        }

        WeatherSample sample = ClientWeatherCoordinator.sampleAt(
                level,
                event.getCamera().getPosition()
        );
        float fog = ClientWeatherCoordinator.fogContribution(sample);
        if (fog <= 0.001F) {
            return;
        }

        float sourceNear = event.getNearPlaneDistance();
        float sourceFar = event.getFarPlaneDistance();
        float targetFar = Math.max(24.0F, sourceFar * (1.0F - fog * 0.8F));
        float denseNear = Math.min(sourceNear, targetFar * 0.25F);
        float targetNear = mix(sourceNear, denseNear, fog);
        event.setNearPlaneDistance(Math.min(targetNear, targetFar - 1.0F));
        event.setFarPlaneDistance(targetFar);
        event.setCanceled(true);
    }

    /**
     * Builds compact F3 lines on demand; normal gameplay performs no debug formatting.
     */
    public static List<String> debugLines() {
        ClientLevel level = Minecraft.getInstance().level;
        ClientWeatherCoordinator.ClientStateView state = ClientWeatherCoordinator.stateView(level);
        if (level == null || state == null) {
            return List.of();
        }

        BlockPos pos = Minecraft.getInstance().player == null
                ? BlockPos.containing(Minecraft.getInstance().gameRenderer.getMainCamera().getPosition())
                : Minecraft.getInstance().player.blockPosition();
        int cellX = Math.floorDiv(pos.getX(), state.cellSize());
        int cellZ = Math.floorDiv(pos.getZ(), state.cellSize());
        WeatherSample sample = ClientWeatherCoordinator.sampleAt(level, pos);
        float thunder = ClientWeatherCoordinator.thunderContribution(sample);
        float fog = ClientWeatherCoordinator.fogContribution(sample);

        return List.of(
                String.format(
                        Locale.ROOT,
                        "WO Atmosphere: cell %d,%d | seq %d | %d cells | blend %.0f%%",
                        cellX,
                        cellZ,
                        state.sequence(),
                        state.cellCount(),
                        state.interpolationProgress() * 100.0D
                ),
                String.format(
                        Locale.ROOT,
                        "T %.1f C | H %.3f | P %.3f | wind %.3f, %.3f",
                        sample.temperature(),
                        sample.humidity(),
                        sample.pressure(),
                        sample.wind().x(),
                        sample.wind().z()
                ),
                String.format(
                        Locale.ROOT,
                        "Cloud %.3f | instability %.3f | storm %.3f",
                        sample.cloudWater(),
                        sample.instability(),
                        sample.stormEnergy()
                ),
                String.format(
                        Locale.ROOT,
                        "Precip %s %.3f | thunder %.3f | fog %.3f",
                        sample.precipitationType(),
                        sample.precipitationIntensity(),
                        thunder,
                        fog
                )
        );
    }

    private static float mix(float from, float to, float amount) {
        return from + (to - from) * Math.max(0.0F, Math.min(1.0F, amount));
    }
}
