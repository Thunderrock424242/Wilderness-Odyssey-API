package com.thunder.wildernessodysseyapi.client.biome;

import com.thunder.ticktoklib.api.TickTokAPI;
import com.thunder.wildernessodysseyapi.core.ModConstants;
import com.thunder.wildernessodysseyapi.worldgen.biome.ModBiomes;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;

@EventBusSubscriber(modid = ModConstants.MOD_ID, value = Dist.CLIENT)
public final class ImpactSiteMusicController {
    private static final int EFFECT_RADIUS = 100;
    private static final int SCAN_STEP = 4;
    private static final int SCAN_VERTICAL = 12;
    private static final int SCAN_INTERVAL_TICKS = TickTokAPI.toTicks(1);

    private static ImpactSiteMusicSoundInstance activeMusic;
    private static BlockPos cachedImpactCenter;
    private static int scanCooldown;

    private ImpactSiteMusicController() {
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        Level level = minecraft.level;
        if (level == null || minecraft.player == null || minecraft.isPaused()) {
            fadeOutAndCleanup(minecraft);
            return;
        }

        BlockPos playerPos = minecraft.player.blockPosition();
        if (!isInAnomalyBiome(level, playerPos)) {
            fadeOutAndCleanup(minecraft);
            return;
        }

        scanCooldown--;
        if (cachedImpactCenter == null || scanCooldown <= 0 || cachedImpactCenter.distSqr(playerPos) > (EFFECT_RADIUS * EFFECT_RADIUS * 4.0D)) {
            cachedImpactCenter = findNearestImpactCenter(level, playerPos, EFFECT_RADIUS);
            scanCooldown = SCAN_INTERVAL_TICKS;
        }

        float targetVolume = 0.0F;
        if (cachedImpactCenter != null) {
            double distance = Math.sqrt(cachedImpactCenter.distSqr(playerPos));
            if (distance <= EFFECT_RADIUS) {
                targetVolume = (float) (1.0D - (distance / EFFECT_RADIUS));
            }
        }

        if (targetVolume > 0.001F) {
            ensureMusicStarted(minecraft);
        }

        if (activeMusic != null) {
            activeMusic.setTargetVolume(targetVolume);
            if (activeMusic.isStopped()) {
                activeMusic = null;
            }
        }
    }

    private static void ensureMusicStarted(Minecraft minecraft) {
        if (activeMusic != null && minecraft.getSoundManager().isActive(activeMusic)) {
            return;
        }

        activeMusic = new ImpactSiteMusicSoundInstance();
        minecraft.getSoundManager().play(activeMusic);
    }

    private static void fadeOutAndCleanup(Minecraft minecraft) {
        cachedImpactCenter = null;
        scanCooldown = 0;

        if (activeMusic == null) {
            return;
        }

        activeMusic.setTargetVolume(0.0F);
        if (!minecraft.getSoundManager().isActive(activeMusic) || activeMusic.isStopped()) {
            activeMusic = null;
        }
    }

    private static BlockPos findNearestImpactCenter(Level level, BlockPos playerPos, int radius) {
        BlockPos bestPos = null;
        double bestDistanceSq = Double.MAX_VALUE;
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();

        for (int dx = -radius; dx <= radius; dx += SCAN_STEP) {
            for (int dz = -radius; dz <= radius; dz += SCAN_STEP) {
                for (int dy = -SCAN_VERTICAL; dy <= SCAN_VERTICAL; dy += SCAN_STEP) {
                    cursor.set(playerPos.getX() + dx, playerPos.getY() + dy, playerPos.getZ() + dz);
                    if (!level.isLoaded(cursor) || !level.getBlockState(cursor).is(Blocks.CRYING_OBSIDIAN)) {
                        continue;
                    }

                    double distSq = cursor.distSqr(playerPos);
                    if (distSq <= radius * radius && distSq < bestDistanceSq) {
                        bestDistanceSq = distSq;
                        bestPos = cursor.immutable();
                    }
                }
            }
        }

        return bestPos;
    }

    private static boolean isInAnomalyBiome(Level level, BlockPos pos) {
        Holder<Biome> biome = level.getBiome(pos);
        return biome.is(ModBiomes.ANOMALY_PLAINS_KEY)
                || biome.is(ModBiomes.ANOMALY_TUNDRA_KEY)
                || biome.is(ModBiomes.ANOMALY_RAINFOREST_KEY);
    }
}
