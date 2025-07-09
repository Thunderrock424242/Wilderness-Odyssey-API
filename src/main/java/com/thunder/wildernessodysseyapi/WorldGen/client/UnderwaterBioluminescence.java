package com.thunder.wildernessodysseyapi.WorldGen.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.event.entity.living.LivingFallEvent;

import java.util.Set;

@OnlyIn(Dist.CLIENT)
public class UnderwaterBioluminescence {

    private static final Minecraft MC = Minecraft.getInstance();
    private static final RandomSource RANDOM = RandomSource.create();

    private static final Set<ResourceKey<Biome>> GLOW_BIOMES = Set.of(
            Biomes.WARM_OCEAN,
            Biomes.LUKEWARM_OCEAN,
            Biomes.DEEP_LUKEWARM_OCEAN,
            Biomes.DEEP_OCEAN
    );

    private static final DustParticleOptions GLOW_COLOR = new DustParticleOptions(
            new net.minecraft.world.phys.Vec3(0.0f, 0.81f, 1.0f).toVector3f(), 1.0f
    );

    private static int clusterTimer = 0;

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        if (MC.level == null || MC.player == null) return;

        Level level = MC.level;
        LocalPlayer player = MC.player;

        float time = level.getTimeOfDay(1.0f);
        if (time < 13000 || time > 23000) return;
        if (!player.isInWater()) return;

        BlockPos center = player.blockPosition();
        ResourceKey<Biome> biomeKey = level.registryAccess().registryOrThrow(Registries.BIOME)
                .getResourceKey(level.getBiome(center).value()).orElse(null);

        if (biomeKey == null || !GLOW_BIOMES.contains(biomeKey)) return;

        // Shoreline Glow
        boolean isShallow = (player.getY() % 1.0f < 0.2f);

        for (int i = 0; i < (isShallow ? 8 : 4); i++) {
            double x = player.getX() + (RANDOM.nextDouble() - 0.5) * 8;
            double y = player.getY() + (RANDOM.nextDouble() - 0.5) * 2;
            double z = player.getZ() + (RANDOM.nextDouble() - 0.5) * 8;

            BlockPos pos = BlockPos.containing(x, y, z);
            if (level.getBlockState(pos).getFluidState().getType() == Fluids.WATER &&
                    level.getBlockState(pos).getBlock() == Blocks.WATER) {
                level.addParticle(GLOW_COLOR, x, y, z, 0.0, 0.002, 0.0);
            }
        }

        // Cluster Burst
        if (++clusterTimer >= 200) {
            clusterTimer = 0;
            for (int i = 0; i < 30; i++) {
                double x = player.getX() + (RANDOM.nextDouble() - 0.5) * 6;
                double y = player.getY() + (RANDOM.nextDouble() - 0.3) * 2;
                double z = player.getZ() + (RANDOM.nextDouble() - 0.5) * 6;
                level.addParticle(GLOW_COLOR, x, y, z, 0.0, 0.004, 0.0);
            }
        }
    }

    @SubscribeEvent
    public static void onSplash(LivingFallEvent event) {
        Entity entity = event.getEntity();
        if (!entity.level().isClientSide || !entity.isInWater()) return;

        for (int i = 0; i < 40; i++) {
            double x = entity.getX() + (RANDOM.nextDouble() - 0.5) * 2;
            double y = entity.getY() + 0.2 + RANDOM.nextDouble() * 0.5;
            double z = entity.getZ() + (RANDOM.nextDouble() - 0.5) * 2;
            entity.level().addParticle(GLOW_COLOR, x, y, z, 0.0, 0.01, 0.0);
        }
    }
}