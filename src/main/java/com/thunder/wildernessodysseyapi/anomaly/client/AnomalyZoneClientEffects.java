package com.thunder.wildernessodysseyapi.anomaly.client;

import com.thunder.wildernessodysseyapi.anomaly.registry.AnomalyDimensions;
import com.thunder.wildernessodysseyapi.core.ModConstants;
import com.thunder.wildernessodysseyapi.core.ModTags;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.ViewportEvent;

/**
 * Supplies the lightweight client atmosphere shared by the Anomaly dimension
 * and smaller Anomaly Forest incursions in the Overworld.
 */
@EventBusSubscriber(modid = ModConstants.MOD_ID, value = Dist.CLIENT)
public final class AnomalyZoneClientEffects {
    private static final float DIMENSION_FOG_RED = 0.12F;
    private static final float DIMENSION_FOG_GREEN = 0.035F;
    private static final float DIMENSION_FOG_BLUE = 0.19F;
    private static final float BIOME_FOG_BLEND = 0.38F;
    private static final int PARTICLE_INTERVAL_TICKS = 4;

    private AnomalyZoneClientEffects() {
    }

    /** Tints only Anomaly-owned air fog, leaving other dimensions untouched. */
    @SubscribeEvent
    public static void onFogColor(ViewportEvent.ComputeFogColor event) {
        Minecraft minecraft = Minecraft.getInstance();
        Level level = minecraft.level;
        if (level == null) {
            return;
        }

        if (level.dimension().equals(AnomalyDimensions.ANOMALY_DIMENSION_KEY)) {
            event.setRed(DIMENSION_FOG_RED);
            event.setGreen(DIMENSION_FOG_GREEN);
            event.setBlue(DIMENSION_FOG_BLUE);
            return;
        }

        BlockPos cameraPos = BlockPos.containing(event.getCamera().getPosition());
        if (level.getBiome(cameraPos).is(ModTags.Biomes.IS_ANOMALY_FOREST)) {
            event.setRed(mix(event.getRed(), DIMENSION_FOG_RED, BIOME_FOG_BLEND));
            event.setGreen(mix(event.getGreen(), DIMENSION_FOG_GREEN, BIOME_FOG_BLEND));
            event.setBlue(mix(event.getBlue(), DIMENSION_FOG_BLUE, BIOME_FOG_BLEND));
        }
    }

    /** Adds sparse portal motes that mark the Anomaly's unstable space. */
    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        Level level = minecraft.level;
        if (level == null || minecraft.player == null || minecraft.player.tickCount % PARTICLE_INTERVAL_TICKS != 0) {
            return;
        }

        BlockPos playerPos = minecraft.player.blockPosition();
        boolean anomalyDimension = level.dimension().equals(AnomalyDimensions.ANOMALY_DIMENSION_KEY);
        if (!anomalyDimension && !level.getBiome(playerPos).is(ModTags.Biomes.IS_ANOMALY_FOREST)) {
            return;
        }

        RandomSource random = level.getRandom();
        int particleCount = anomalyDimension ? 3 : 1;
        for (int i = 0; i < particleCount; i++) {
            double x = minecraft.player.getX() + random.nextDouble() * 14.0D - 7.0D;
            double y = minecraft.player.getY() + random.nextDouble() * 5.0D - 1.0D;
            double z = minecraft.player.getZ() + random.nextDouble() * 14.0D - 7.0D;
            level.addParticle(ParticleTypes.PORTAL, x, y, z, 0.0D, 0.015D, 0.0D);
            if (anomalyDimension && random.nextInt(7) == 0) {
                level.addParticle(ParticleTypes.ELECTRIC_SPARK, x, y, z, 0.0D, 0.01D, 0.0D);
            }
        }
    }

    private static float mix(float from, float to, float amount) {
        return from + (to - from) * amount;
    }
}
