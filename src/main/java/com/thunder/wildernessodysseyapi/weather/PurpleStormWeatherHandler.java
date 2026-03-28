package com.thunder.wildernessodysseyapi.weather;

import com.thunder.ticktoklib.api.TickTokAPI;
import com.thunder.wildernessodysseyapi.core.ModConstants;
import com.thunder.wildernessodysseyapi.entity.ModEntities;
import com.thunder.wildernessodysseyapi.entity.PurpleStormMonsterEntity;
import com.thunder.wildernessodysseyapi.worldgen.biome.ModBiomes;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.levelgen.Heightmap;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.LevelTickEvent;

@EventBusSubscriber(modid = ModConstants.MOD_ID)
public final class PurpleStormWeatherHandler {
    private PurpleStormWeatherHandler() {
    }

    public static final long HOUR_TICKS = TickTokAPI.toTicksFromHours(1L);
    public static final long EVENT_DURATION_TICKS = TickTokAPI.toTicksFromMinutes(30L);
    private static final int SPAWN_ATTEMPT_INTERVAL_TICKS = TickTokAPI.toTicksFromSeconds(10);

    @SubscribeEvent
    public static void onLevelTick(LevelTickEvent.Post event) {
        if (!(event.getLevel() instanceof ServerLevel serverLevel) || serverLevel.dimension() != ServerLevel.OVERWORLD) {
            return;
        }

        long gameTime = serverLevel.getGameTime();
        PurpleStormSavedData data = getData(serverLevel);

        if (data.nextRollGameTime() == 0L) {
            data.setNextRollGameTime(gameTime + HOUR_TICKS);
        }

        if (data.active()) {
            enforcePurpleStormWeather(serverLevel);
            if (gameTime % SPAWN_ATTEMPT_INTERVAL_TICKS == 0L) {
                trySpawnPurpleStormMonster(serverLevel);
            }
            if (gameTime >= data.eventEndGameTime()) {
                stopStorm(serverLevel, data, gameTime);
            }
            return;
        }

        if (gameTime >= data.nextRollGameTime()) {
            data.setNextRollGameTime(gameTime + HOUR_TICKS);
            if (hasPlayersInAnomalyBiomes(serverLevel) && serverLevel.random.nextFloat() <= 0.5F) {
                startStorm(serverLevel, data, gameTime);
            }
        }
    }

    public static boolean isPurpleStormActive(ServerLevel level) {
        return getData(level).active();
    }

    private static PurpleStormSavedData getData(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(
                new net.minecraft.world.level.saveddata.SavedData.Factory<>(PurpleStormSavedData::new, PurpleStormSavedData::new),
                PurpleStormSavedData.DATA_NAME
        );
    }

    private static void startStorm(ServerLevel level, PurpleStormSavedData data, long gameTime) {
        data.setActive(true);
        data.setEventEndGameTime(gameTime + EVENT_DURATION_TICKS);
        enforcePurpleStormWeather(level);
    }

    private static void stopStorm(ServerLevel level, PurpleStormSavedData data, long gameTime) {
        data.setActive(false);
        data.setEventEndGameTime(0L);
        data.setNextRollGameTime(gameTime + HOUR_TICKS);
        level.setWeatherParameters(12_000, 0, false, false);
    }

    private static void enforcePurpleStormWeather(ServerLevel level) {
        if (!level.getGameRules().getBoolean(GameRules.RULE_WEATHER_CYCLE)) {
            level.getGameRules().getRule(GameRules.RULE_WEATHER_CYCLE).set(true, level.getServer());
        }
        level.setWeatherParameters(0, (int) EVENT_DURATION_TICKS, true, true);
    }

    private static void trySpawnPurpleStormMonster(ServerLevel level) {
        RandomSource random = level.getRandom();
        for (ServerPlayer player : level.players()) {
            if (!isInAnomalyBiome(level, player.blockPosition())) {
                continue;
            }

            if (random.nextFloat() > 0.35F) {
                continue;
            }

            int x = player.blockPosition().getX() + random.nextInt(49) - 24;
            int z = player.blockPosition().getZ() + random.nextInt(49) - 24;
            int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
            BlockPos spawnPos = new BlockPos(x, y, z);
            if (!isInAnomalyBiome(level, spawnPos)) {
                continue;
            }

            if (!level.getBlockState(spawnPos).isAir() || !level.getBlockState(spawnPos.above()).isAir()) {
                continue;
            }

            PurpleStormMonsterEntity monster = ModEntities.PURPLE_STORM_MONSTER.get().create(level);
            if (monster == null) {
                continue;
            }

            monster.moveTo(x + 0.5D, y, z + 0.5D, random.nextFloat() * 360.0F, 0.0F);
            if (PurpleStormMonsterEntity.checkPurpleStormSpawnRules(ModEntities.PURPLE_STORM_MONSTER.get(), level, MobSpawnType.EVENT, spawnPos, random)) {
                level.addFreshEntity(monster);
            }
        }
    }

    private static boolean hasPlayersInAnomalyBiomes(ServerLevel level) {
        for (ServerPlayer player : level.players()) {
            if (isInAnomalyBiome(level, player.blockPosition())) {
                return true;
            }
        }
        return false;
    }

    private static boolean isInAnomalyBiome(ServerLevel level, BlockPos pos) {
        Holder<Biome> biome = level.getBiome(pos);
        return biome.is(ModBiomes.ANOMALY_PLAINS_KEY)
                || biome.is(ModBiomes.ANOMALY_TUNDRA_KEY)
                || biome.is(ModBiomes.ANOMALY_RAINFOREST_KEY)
                || biome.is(ModBiomes.ANOMALY_ZONE_KEY)
                || biome.is(ModBiomes.ANOMALY_DESERT_KEY);
    }
}
