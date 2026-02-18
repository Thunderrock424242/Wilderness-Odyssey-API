package com.thunder.wildernessodysseyapi.client.biome;

import com.thunder.wildernessodysseyapi.Core.ModConstants;
import com.thunder.wildernessodysseyapi.WorldGen.biome.ModBiomes;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.ViewportEvent;

@EventBusSubscriber(modid = ModConstants.MOD_ID, value = Dist.CLIENT)
public final class AnomalyZoneClientEffects {
    private AnomalyZoneClientEffects() {
    }

    @SubscribeEvent
    public static void onFogColor(ViewportEvent.ComputeFogColor event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null || minecraft.player == null || !isInAnomalyBiome(minecraft.level, minecraft.player.blockPosition())) {
            return;
        }

        event.setRed(event.getRed() * 0.75F + 0.25F);
        event.setGreen(event.getGreen() * 0.45F);
        event.setBlue(Math.min(1.0F, event.getBlue() * 0.85F + 0.2F));
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        Level level = minecraft.level;
        if (level == null || minecraft.player == null) {
            return;
        }

        BlockPos playerPos = minecraft.player.blockPosition();
        if (!isInAnomalyBiome(level, playerPos)) {
            return;
        }

        RandomSource random = level.getRandom();

        // Constant distortion/ripple-like ambience.
        for (int i = 0; i < 4; i++) {
            double x = playerPos.getX() + random.nextDouble() * 12.0D - 6.0D;
            double y = playerPos.getY() + random.nextDouble() * 2.0D + 0.5D;
            double z = playerPos.getZ() + random.nextDouble() * 12.0D - 6.0D;
            level.addParticle(ParticleTypes.PORTAL, x, y, z, 0.0D, 0.03D, 0.0D);
        }

        // Occasional lightning-like cracks.
        if (random.nextInt(120) == 0) {
            double x = playerPos.getX() + random.nextDouble() * 24.0D - 12.0D;
            double y = playerPos.getY() + random.nextDouble() * 8.0D + 6.0D;
            double z = playerPos.getZ() + random.nextDouble() * 24.0D - 12.0D;
            level.addParticle(ParticleTypes.ELECTRIC_SPARK, x, y, z, 0.0D, -0.25D, 0.0D);
        }
    }

    private static boolean isInAnomalyBiome(Level level, BlockPos pos) {
        Holder<Biome> biome = level.getBiome(pos);
        return biome.is(ModBiomes.ANOMALY_PLAINS_KEY)
                || biome.is(ModBiomes.ANOMALY_DESERT_KEY)
                || biome.is(ModBiomes.ANOMALY_TUNDRA_KEY)
                || biome.is(ModBiomes.ANOMALY_RAINFOREST_KEY);
    }
}
