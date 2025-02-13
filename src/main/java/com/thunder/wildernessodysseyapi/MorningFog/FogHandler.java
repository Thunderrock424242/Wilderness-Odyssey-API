package com.thunder.wildernessodysseyapi.MorningFog;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.dimension.DimensionType;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;

@EventBusSubscriber
public class FogHandler {

    // Time-of-day constants for smooth transitions
    private static final long MORNING_START_TICK = 2000;
    private static final long MORNING_END_TICK   = 5000;
    private static final float BASE_DENSITY      = 0.025f;

    // Base color for maximum dew effect
    private static final float BASE_R = 0.85f;
    private static final float BASE_G = 0.90f;
    private static final float BASE_B = 0.95f;

    @SubscribeEvent
    public static void onFogColors(EntityViewRenderEvent.FogColors event) {
        Minecraft mc = Minecraft.getInstance();
        Level level = mc.level;
        Player player = mc.player;
        if (level == null || player == null) return;

        float dewFactor = computeDewFactor(level, player);
        if (dewFactor <= 0.0f) {
            // No morning dew effect
            return;
        }

        // Blend between the default fog color and our dew color
        float originalR = event.getRed();
        float originalG = event.getGreen();
        float originalB = event.getBlue();

        float newR = (1f - dewFactor) * originalR + dewFactor * BASE_R;
        float newG = (1f - dewFactor) * originalG + dewFactor * BASE_G;
        float newB = (1f - dewFactor) * originalB + dewFactor * BASE_B;

        event.setRed(newR);
        event.setGreen(newG);
        event.setBlue(newB);
    }

    @SubscribeEvent
    public static void onFogDensity(EntityViewRenderEvent.FogDensity event) {
        Minecraft mc = Minecraft.getInstance();
        Level level = mc.level;
        Player player = mc.player;
        if (level == null || player == null) return;

        float dewFactor = computeDewFactor(level, player);
        if (dewFactor <= 0.0f) {
            return;
        }

        float density = BASE_DENSITY * dewFactor;
        event.setDensity(density);
        // Must cancel or the default fog density code overrides ours
        event.setCanceled(true);
    }

    /**
     * Computes a 0..1 "dew factor" based on time of day, biome humidity, and weather.
     */
    private static float computeDewFactor(Level level, Player player) {
        // 1) Time of Day Factor
        long timeOfDay = level.getDayTime() % 24000;
        float timeFactor = 0f;
        if (timeOfDay >= MORNING_START_TICK && timeOfDay <= MORNING_END_TICK) {
            float range = (MORNING_END_TICK - MORNING_START_TICK);
            timeFactor = (timeOfDay - MORNING_START_TICK) / range; // 0..1
        }

        // 2) Biome Factor (humidity)
        BlockPos pos = player.blockPosition();
        Biome biome = level.getBiome(pos).value();
        float humidity = biome.climateSettings().downfall();
        float biomeFactor = 0.5f + (humidity * 0.5f); // scale humidity into 0.5..1.0

        // 3) Weather Factor
        boolean isRaining = level.isRainingAt(pos);
        float weatherFactor = isRaining ? 1.25f : 1.0f;

        // 4) Dimension Check
        if (!isOverworld(level)) {
            return 0f;
        }

        // Combine all
        float dewFactor = timeFactor * biomeFactor * weatherFactor;
        if (dewFactor > 1.0f) {
            dewFactor = 1.0f;
        }
        return dewFactor;
    }

    private static boolean isOverworld(Level level) {
        DimensionType dimensionType = level.dimensionType();
        return dimensionType.hasSkyLight();
    }
}