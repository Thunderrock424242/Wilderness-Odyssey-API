package com.thunder.wildernessodysseyapi.environment.glacial.client;

import com.thunder.wildernessodysseyapi.core.ModConstants;
import com.thunder.wildernessodysseyapi.environment.glacial.GlacialBiomeManager;
import com.thunder.wildernessodysseyapi.environment.glacial.GlacialSeasonSnapshot;
import com.thunder.wildernessodysseyapi.environment.glacial.config.GlacialConfig;
import com.thunder.wildernessodysseyapi.weather.api.WeatherSample;
import com.thunder.wildernessodysseyapi.weather.client.ClientWeatherCoordinator;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.levelgen.Heightmap;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.event.level.LevelEvent;

/** Sparse, player-local polar particles and ambience driven by existing weather state. */
@EventBusSubscriber(modid = ModConstants.MOD_ID, value = Dist.CLIENT)
public final class GlacialAmbientManager {

    private static long nextSoundTick;

    private GlacialAmbientManager() {
    }

    /** Emits bounded local effects only while the player occupies a glacial biome. */
    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        ClientLevel level = minecraft.level;
        Player player = minecraft.player;
        if (level == null || player == null) {
            return;
        }
        GlacialBiomeManager.Family family = GlacialBiomeManager
                .environmentalFamily(level.getBiome(player.blockPosition()))
                .orElse(null);
        if (family == null) {
            return;
        }
        WeatherSample weather = ClientWeatherCoordinator.localSample(level);
        GlacialSeasonSnapshot season = ClientGlacialState.snapshot(level);
        RandomSource random = level.getRandom();
        if (GlacialConfig.ENABLE_BLOWING_SNOW_EFFECTS.get() && level.getGameTime() % 4L == 0L) {
            spawnSnow(level, player, weather, season, family, random);
            spawnMeltEffects(level, player, season, family, random);
        }
        if (GlacialConfig.ENABLE_GLACIER_AMBIENT_SOUNDS.get()
                && level.getGameTime() >= nextSoundTick) {
            playSparseSound(level, player, season, random);
            nextSoundTick = level.getGameTime() + 180L + random.nextInt(420);
        }
    }

    /** Resets timing on dimension unload so ambience never leaks between worlds. */
    @SubscribeEvent
    public static void onLevelUnload(LevelEvent.Unload event) {
        if (event.getLevel() instanceof ClientLevel) {
            nextSoundTick = 0L;
        }
    }

    private static void spawnSnow(
            ClientLevel level,
            Player player,
            WeatherSample weather,
            GlacialSeasonSnapshot season,
            GlacialBiomeManager.Family family,
            RandomSource random
    ) {
        double wind = Math.min(1.0, weather.wind().magnitude());
        int exposedBonus = family == GlacialBiomeManager.Family.POLAR_ICE_SHEET
                || family == GlacialBiomeManager.Family.GLACIAL_HIGHLANDS ? 1 : 0;
        int count = 1 + exposedBonus + (int) Math.floor(wind * 2.0 + season.freezeFraction());
        for (int index = 0; index < count; index++) {
            double x = player.getX() + (random.nextDouble() - 0.5) * 14.0;
            double y = player.getY() + 1.0 + random.nextDouble() * 6.0;
            double z = player.getZ() + (random.nextDouble() - 0.5) * 14.0;
            level.addParticle(
                    ParticleTypes.SNOWFLAKE,
                    x,
                    y,
                    z,
                    weather.wind().x() * 0.12,
                    -0.025 - random.nextDouble() * 0.03,
                    weather.wind().z() * 0.12
            );
        }
        if (wind > 0.55 && random.nextInt(5) == 0) {
            double x = player.getX() + (random.nextDouble() - 0.5) * 10.0;
            double y = player.getY() + 0.15 + random.nextDouble() * 1.2;
            double z = player.getZ() + (random.nextDouble() - 0.5) * 10.0;
            level.addParticle(
                    ParticleTypes.CLOUD,
                    x,
                    y,
                    z,
                    weather.wind().x() * 0.18,
                    0.005,
                    weather.wind().z() * 0.18
            );
        }
    }

    private static void spawnMeltEffects(
            ClientLevel level,
            Player player,
            GlacialSeasonSnapshot season,
            GlacialBiomeManager.Family family,
            RandomSource random
    ) {
        if (season.meltFraction() < 0.45 || random.nextInt(3) != 0) {
            return;
        }
        boolean meltwaterFamily = family == GlacialBiomeManager.Family.MELTWATER_VALLEY
                || family == GlacialBiomeManager.Family.GLACIAL_BASIN
                || family == GlacialBiomeManager.Family.ICEBERG_COAST;
        if (!meltwaterFamily) {
            return;
        }
        int x = player.getBlockX() + random.nextInt(13) - 6;
        int z = player.getBlockZ() + random.nextInt(13) - 6;
        BlockPos sample = new BlockPos(x, player.getBlockY(), z);
        if (!level.hasChunkAt(sample)) {
            return;
        }
        int surfaceY = level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z) - 1;
        BlockPos surface = new BlockPos(x, surfaceY, z);
        if (level.getFluidState(surface).is(FluidTags.WATER)) {
            level.addParticle(
                    ParticleTypes.SPLASH,
                    x + random.nextDouble(),
                    surfaceY + 1.02,
                    z + random.nextDouble(),
                    0.0,
                    0.02,
                    0.0
            );
        } else if (!level.canSeeSky(player.blockPosition().above()) && random.nextBoolean()) {
            level.addParticle(
                    ParticleTypes.DRIPPING_WATER,
                    player.getX() + (random.nextDouble() - 0.5) * 5.0,
                    player.getY() + 2.5 + random.nextDouble() * 2.0,
                    player.getZ() + (random.nextDouble() - 0.5) * 5.0,
                    0.0,
                    0.0,
                    0.0
            );
        }
    }

    private static void playSparseSound(
            ClientLevel level,
            Player player,
            GlacialSeasonSnapshot season,
            RandomSource random
    ) {
        BlockPos position = player.blockPosition();
        boolean cave = !level.canSeeSky(position.above());
        if (cave && season.meltFraction() > 0.35) {
            level.playLocalSound(player.getX(), player.getY(), player.getZ(),
                    SoundEvents.POINTED_DRIPSTONE_DRIP_WATER, SoundSource.AMBIENT,
                    0.18F, 0.75F + random.nextFloat() * 0.25F, false);
        } else if (season.meltFraction() > 0.65 && random.nextBoolean()) {
            level.playLocalSound(player.getX(), player.getY(), player.getZ(),
                    SoundEvents.WATER_AMBIENT, SoundSource.AMBIENT,
                    0.12F, 0.85F + random.nextFloat() * 0.2F, false);
        } else if (random.nextInt(3) == 0) {
            level.playLocalSound(player.getX(), player.getY(), player.getZ(),
                    SoundEvents.GLASS_BREAK, SoundSource.AMBIENT,
                    0.07F, 0.45F + random.nextFloat() * 0.15F, true);
        } else {
            level.playLocalSound(player.getX(), player.getY(), player.getZ(),
                    SoundEvents.AMBIENT_CAVE.value(), SoundSource.AMBIENT,
                    0.11F, 0.55F + random.nextFloat() * 0.2F, true);
        }
    }
}
