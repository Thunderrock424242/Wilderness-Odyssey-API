package com.thunder.wildernessodysseyapi.client.weather;

import com.thunder.wildernessodysseyapi.core.ModConstants;
import com.thunder.wildernessodysseyapi.worldgen.biome.ModBiomes;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.ViewportEvent;
import org.joml.Vector3f;

@EventBusSubscriber(modid = ModConstants.MOD_ID, value = Dist.CLIENT)
public final class PurpleStormClientEffects {
    private static final Vector3f PURPLE_RAIN_COLOR = new Vector3f(0.62F, 0.25F, 0.9F);

    private PurpleStormClientEffects() {
    }

    @SubscribeEvent
    public static void onFogColor(ViewportEvent.ComputeFogColor event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null || !isPurpleStormVisualActive(minecraft.level)) {
            return;
        }

        event.setRed(0.46F);
        event.setGreen(0.18F);
        event.setBlue(0.58F);
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        Level level = minecraft.level;
        if (level == null || minecraft.player == null || !isPurpleStormVisualActive(level)) {
            return;
        }

        RandomSource random = level.getRandom();
        BlockPos playerPos = minecraft.player.blockPosition();

        for (int i = 0; i < 8; i++) {
            double x = playerPos.getX() + random.nextDouble() * 20.0D - 10.0D;
            double y = playerPos.getY() + 8.0D + random.nextDouble() * 6.0D;
            double z = playerPos.getZ() + random.nextDouble() * 20.0D - 10.0D;
            level.addParticle(new DustParticleOptions(PURPLE_RAIN_COLOR, 1.0F), x, y, z, 0.0D, -0.35D, 0.0D);
        }
    }

    private static boolean isPurpleStormVisualActive(Level level) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null) {
            return false;
        }

        return level.isRaining()
                && level.isThundering()
                && isInAnomalyBiome(level, minecraft.player.blockPosition());
    }

    private static boolean isInAnomalyBiome(Level level, BlockPos pos) {
        Holder<Biome> biome = level.getBiome(pos);
        return biome.is(ModBiomes.ANOMALY_PLAINS_KEY)
                || biome.is(ModBiomes.ANOMALY_TUNDRA_KEY)
                || biome.is(ModBiomes.ANOMALY_RAINFOREST_KEY)
                || biome.is(ModBiomes.ANOMALY_ZONE_KEY)
                || biome.is(ModBiomes.ANOMALY_DESERT_KEY);
    }
}
