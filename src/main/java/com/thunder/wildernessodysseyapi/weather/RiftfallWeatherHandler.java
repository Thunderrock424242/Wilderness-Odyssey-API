package com.thunder.wildernessodysseyapi.weather;

import com.thunder.ticktoklib.api.TickTokAPI;
import com.thunder.wildernessodysseyapi.core.ModConstants;
import com.thunder.wildernessodysseyapi.core.ModEntities;
import com.thunder.wildernessodysseyapi.entity.RiftbornEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.LevelTickEvent;

@EventBusSubscriber(modid = ModConstants.MOD_ID)
public final class RiftfallWeatherHandler {
    private RiftfallWeatherHandler() {
    }

    public static final long HOUR_TICKS = TickTokAPI.toTicksFromHours(1L);
    public static final long EVENT_DURATION_TICKS = TickTokAPI.toTicksFromMinutes(30L);
    private static final int SPAWN_ATTEMPT_INTERVAL_TICKS = TickTokAPI.toTicksFromSeconds(10);

    @SubscribeEvent
    public static void onLevelTick(LevelTickEvent.Post event) {
        if (!(event.getLevel() instanceof ServerLevel serverLevel) || serverLevel.dimension() != Level.OVERWORLD) {
            return;
        }

        long gameTime = serverLevel.getGameTime();
        RiftfallWeatherSavedData data = getData(serverLevel);

        if (data.nextRollGameTime() == 0L) {
            data.setNextRollGameTime(gameTime + HOUR_TICKS);
        }

        if (data.active()) {
            enforceRiftfallWeather(serverLevel);
            if (gameTime % SPAWN_ATTEMPT_INTERVAL_TICKS == 0L) {
                trySpawnRiftborn(serverLevel);
            }
            if (gameTime >= data.eventEndGameTime()) {
                stopRiftfall(serverLevel, data, gameTime);
            }
            return;
        }

        if (gameTime >= data.nextRollGameTime()) {
            data.setNextRollGameTime(gameTime + HOUR_TICKS);
            if (!serverLevel.players().isEmpty() && serverLevel.random.nextFloat() <= 0.5F) {
                startRiftfall(serverLevel, data, gameTime);
            }
        }
    }

    public static boolean isRiftfallActive(ServerLevel level) {
        return getData(level).active();
    }

    private static RiftfallWeatherSavedData getData(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(
                new net.minecraft.world.level.saveddata.SavedData.Factory<>(RiftfallWeatherSavedData::new, RiftfallWeatherSavedData::new),
                RiftfallWeatherSavedData.DATA_NAME
        );
    }

    private static void startRiftfall(ServerLevel level, RiftfallWeatherSavedData data, long gameTime) {
        data.setActive(true);
        data.setEventEndGameTime(gameTime + EVENT_DURATION_TICKS);
        enforceRiftfallWeather(level);
    }

    private static void stopRiftfall(ServerLevel level, RiftfallWeatherSavedData data, long gameTime) {
        data.setActive(false);
        data.setEventEndGameTime(0L);
        data.setNextRollGameTime(gameTime + HOUR_TICKS);
        level.setWeatherParameters(12_000, 0, false, false);
    }

    private static void enforceRiftfallWeather(ServerLevel level) {
        if (!level.getGameRules().getBoolean(GameRules.RULE_WEATHER_CYCLE)) {
            level.getGameRules().getRule(GameRules.RULE_WEATHER_CYCLE).set(true, level.getServer());
        }
        level.setWeatherParameters(0, (int) EVENT_DURATION_TICKS, true, true);
    }

    private static void trySpawnRiftborn(ServerLevel level) {
        RandomSource random = level.getRandom();
        for (ServerPlayer player : level.players()) {
            if (random.nextFloat() > 0.35F) {
                continue;
            }

            int x = player.blockPosition().getX() + random.nextInt(49) - 24;
            int z = player.blockPosition().getZ() + random.nextInt(49) - 24;
            int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
            BlockPos spawnPos = new BlockPos(x, y, z);

            if (!level.getBlockState(spawnPos).isAir() || !level.getBlockState(spawnPos.above()).isAir()) {
                continue;
            }

            RiftbornEntity monster = ModEntities.RIFTBORN.get().create(level);
            if (monster == null) {
                continue;
            }

            monster.moveTo(x + 0.5D, y, z + 0.5D, random.nextFloat() * 360.0F, 0.0F);
            if (RiftbornEntity.checkRiftbornSpawnRules(ModEntities.RIFTBORN.get(), level, MobSpawnType.EVENT, spawnPos, random)) {
                level.addFreshEntity(monster);
            }
        }
    }
}
