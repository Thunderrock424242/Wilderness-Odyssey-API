package com.thunder.wildernessodysseyapi.weather.client;

import com.thunder.wildernessodysseyapi.core.ModConstants;
import com.thunder.wildernessodysseyapi.weather.api.WeatherSample;
import com.thunder.wildernessodysseyapi.weather.client.cloud.CloudFieldSample;
import com.thunder.wildernessodysseyapi.weather.client.cloud.CloudLightingModel;
import com.thunder.wildernessodysseyapi.weather.client.cloud.LocalizedCloudRenderer;
import com.thunder.wildernessodysseyapi.weather.client.precipitation.LocalizedPrecipitationRenderer;
import com.thunder.wildernessodysseyapi.weather.client.precipitation.WeatherImpactRenderer;
import com.thunder.wildernessodysseyapi.weather.client.surface.WeatherSurfaceRenderer;
import com.thunder.wildernessodysseyapi.weather.config.WeatherRenderingConfig;
import net.minecraft.client.CloudStatus;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.material.FogType;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.ViewportEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.event.level.LevelEvent;
import com.thunder.wildernessodysseyapi.weather.simulation.WeatherHazardModel;

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

    /** Releases dormant cloud geometry when the normal cloud render call is skipped. */
    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.options.getCloudsType() == CloudStatus.OFF
                || !WeatherRenderingConfig.settings().enabled()
                || !ClientWeatherCoordinator.controls(minecraft.level)) {
            LocalizedCloudRenderer.clear();
        }
        if (!ClientWeatherCoordinator.controls(minecraft.level)) {
            LocalizedPrecipitationRenderer.clear();
            WeatherImpactRenderer.clear();
            WeatherSurfaceRenderer.clear();
        }
    }

    /** Clears stale sequence watermarks before the next server starts syncing weather. */
    @SubscribeEvent
    public static void onLogin(ClientPlayerNetworkEvent.LoggingIn event) {
        LocalizedCloudRenderer.clear();
        LocalizedPrecipitationRenderer.clear();
        WeatherImpactRenderer.clear();
        WeatherSurfaceRenderer.clear();
        ClientWeatherCoordinator.clearAll();
    }

    /** Releases all immutable atmosphere state when the client disconnects. */
    @SubscribeEvent
    public static void onLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        LocalizedCloudRenderer.clear();
        LocalizedPrecipitationRenderer.clear();
        WeatherImpactRenderer.clear();
        WeatherSurfaceRenderer.clear();
        ClientWeatherCoordinator.clearAll();
    }

    /** Clears the displayed region immediately during a dimension unload. */
    @SubscribeEvent
    public static void onLevelUnload(LevelEvent.Unload event) {
        if (event.getLevel() instanceof ClientLevel clientLevel) {
            LocalizedCloudRenderer.clear();
            LocalizedPrecipitationRenderer.clear();
            WeatherImpactRenderer.clear();
            WeatherSurfaceRenderer.clear();
            ClientWeatherCoordinator.clearLevel(clientLevel);
        }
    }

    /** Draws cosmetic wet-ground and puddle response after translucent terrain. */
    @SubscribeEvent
    public static void onRenderLevel(RenderLevelStageEvent event) {
        WeatherSurfaceRenderer.render(event);
        WeatherImpactRenderer.render(event);
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
        CloudFieldSample cloudField = ClientWeatherCoordinator.cloudFieldAt(
                level,
                event.getCamera().getPosition()
        );
        float fog = (float) CloudLightingModel.fogContribution(
                sample,
                cloudField,
                event.getCamera().getPosition().y,
                level.effects().getCloudHeight()
        );
        WeatherHazardModel.HazardProfile hazards = WeatherHazardModel.evaluate(sample);
        fog = Math.max(fog, (float) Math.max(hazards.denseFog() * 0.92, hazards.blizzard() * 0.78));
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
        CloudFieldSample cloudField = ClientWeatherCoordinator.cloudFieldAt(
                level,
                event.getCamera().getPosition()
        );
        float fog = (float) CloudLightingModel.fogContribution(
                sample,
                cloudField,
                event.getCamera().getPosition().y,
                level.effects().getCloudHeight()
        );
        WeatherHazardModel.HazardProfile hazards = WeatherHazardModel.evaluate(sample);
        fog = Math.max(fog, (float) Math.max(hazards.denseFog() * 0.92, hazards.blizzard() * 0.82));
        if (fog <= 0.001F) {
            return;
        }

        float sourceNear = event.getNearPlaneDistance();
        float sourceFar = event.getFarPlaneDistance();
        float targetFar = (float) CloudLightingModel.attenuatedFogFarPlane(sourceFar, fog);
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
        CloudFieldSample cloudField = ClientWeatherCoordinator.cloudFieldAt(
                level,
                pos.getX() + 0.5,
                pos.getZ() + 0.5
        );
        CloudLightingModel.OpticalState optics = CloudLightingModel.evaluate(cloudField);
        float fog = (float) CloudLightingModel.fogContribution(
                sample,
                cloudField,
                pos.getY() + 0.5,
                level.effects().getCloudHeight()
        );
        LocalizedCloudRenderer.Diagnostics clouds = LocalizedCloudRenderer.diagnostics();
        LocalizedPrecipitationRenderer.Diagnostics precipitation =
                LocalizedPrecipitationRenderer.diagnostics();
        WeatherHazardModel.HazardProfile hazards = WeatherHazardModel.evaluate(sample);

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
                        "T %.1f C | dew %.1f C | H %.3f | P %.3f",
                        sample.temperature(),
                        sample.dewPointCelsius(),
                        sample.humidity(),
                        sample.pressure()
                ),
                String.format(
                        Locale.ROOT,
                        "Cloud %s %.3f depth %.3f | lift %.3f | storm %s %.3f",
                        sample.cloudType().displayName(),
                        sample.cloudWater(),
                        sample.cloudDepth(),
                        sample.verticalMotion(),
                        sample.stormStage(),
                        sample.stormEnergy()
                ),
                String.format(
                        Locale.ROOT,
                        "Surface wind %.3f, %.3f | cloud wind %.3f, %.3f",
                        sample.wind().x(),
                        sample.wind().z(),
                        sample.cloudWind().x(),
                        sample.cloudWind().z()
                ),
                String.format(
                        Locale.ROOT,
                        "Precip %s %.3f | thunder %.3f | fog %.3f",
                        sample.precipitationType(),
                        sample.precipitationIntensity(),
                        thunder,
                        fog
                ),
                String.format(
                        Locale.ROOT,
                        "Phenomenon %s %.3f | ground wet %.3f puddle %.3f snow %.3f frozen %.3f",
                        hazards.dominant(),
                        hazards.dominantIntensity(),
                        sample.surface().wetness(),
                        sample.surface().puddleCoverage(),
                        sample.surface().snowpack(),
                        sample.surface().frozenFraction()
                ),
                String.format(
                        Locale.ROOT,
                        "Cloud render %s %s/%d | %s | %d samples | %d vertices | coverage %.3f",
                        clouds.active() ? "active" : "inactive",
                        clouds.mode(),
                        clouds.layers(),
                        clouds.cloudType(),
                        clouds.visibleTiles(),
                        clouds.vertices(),
                        clouds.averageCoverage()
                ),
                String.format(
                        Locale.ROOT,
                        "Cloud field %dx%d @ %d blocks | light %d | blend %.0f%% | bands 0x%X | distant %s",
                        clouds.fieldWidth(),
                        clouds.fieldHeight(),
                        clouds.fieldSpacing(),
                        clouds.lightingSteps(),
                        clouds.fieldBlend() * 100.0,
                        clouds.bandMask(),
                        clouds.distantField() ? "on" : "off"
                ),
                String.format(
                        Locale.ROOT,
                        "Overhead coverage %.3f | optical %.3f | shadow %.3f",
                        optics.coverage(),
                        optics.opticalDensity(),
                        optics.shadow()
                ),
                String.format(
                        Locale.ROOT,
                        "Precip mesh %s | %d near | %d shafts | %d vertices | %d impacts",
                        precipitation.active() ? "active" : "inactive",
                        precipitation.nearColumns(),
                        precipitation.distantShafts(),
                        precipitation.vertices(),
                        WeatherImpactRenderer.activeImpactCount()
                )
        );
    }

    private static float mix(float from, float to, float amount) {
        return from + (to - from) * Math.max(0.0F, Math.min(1.0F, amount));
    }
}
