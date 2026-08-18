package com.thunder.wildernessodysseyapi.weather.lightning;

import com.thunder.wildernessodysseyapi.environment.event.WorldDisturbanceService;
import com.thunder.wildernessodysseyapi.environment.event.WorldDisturbanceType;
import com.thunder.wildernessodysseyapi.mixin.ServerLevelLightningAccessor;
import com.thunder.wildernessodysseyapi.weather.api.AtmosphereCellKey;
import com.thunder.wildernessodysseyapi.weather.api.WeatherQuery;
import com.thunder.wildernessodysseyapi.weather.api.WeatherSample;
import com.thunder.wildernessodysseyapi.weather.config.WeatherConfig;
import com.thunder.wildernessodysseyapi.weather.integration.LocalizedPrecipitationController;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.animal.horse.SkeletonHorse;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.block.LightningRodBlock;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Schedules real vanilla lightning from authoritative localized storms.
 *
 * <p>One instance belongs to one {@code WeatherAuthority} level runtime. It
 * samples only a fixed number of player-relevant columns, never requests an
 * unloaded chunk, and creates at most one bolt per scheduler check. Minecraft
 * still owns rod attraction, skeleton traps, fire, copper, entity effects,
 * sound, sky flash, and entity synchronization.</p>
 */
public final class LocalizedLightningScheduler {

    private static final int MAX_TRACKED_CELL_COOLDOWNS = 4_096;

    private final WeatherQuery weather;
    private final LocalizedPrecipitationController precipitation;
    private final Map<Long, Long> nextCellStrikeTick = new HashMap<>();

    private long nextCheckTick = Long.MIN_VALUE;
    private long nextDimensionStrikeTick = Long.MIN_VALUE;
    private int playerCursor;

    /** Creates a level runtime scheduler backed by one authoritative query. */
    public LocalizedLightningScheduler(WeatherQuery weather) {
        this.weather = Objects.requireNonNull(weather, "weather");
        this.precipitation = new LocalizedPrecipitationController(weather);
    }

    /**
     * Performs one due, bounded server-side lightning check.
     *
     * @return whether a real lightning entity was added
     */
    public boolean tick(
            ServerLevel level,
            long gameTime,
            int atmosphericCellSize,
            WeatherConfig.LightningSettings settings
    ) {
        Objects.requireNonNull(level, "level");
        WeatherConfig.LightningSettings safeSettings = Objects.requireNonNullElse(
                settings,
                WeatherConfig.LightningSettings.DEFAULT
        );
        if (!safeSettings.enabled() || gameTime < nextCheckTick) {
            return false;
        }

        // A stateful deadline avoids repeated work if a paused level reports
        // the same game time across multiple server ticks.
        nextCheckTick = safeAdd(gameTime, safeSettings.checkIntervalTicks());
        List<ServerPlayer> players = nonSpectatorPlayers(level.players());
        if (players.isEmpty()) {
            return false;
        }

        pruneExpiredCellCooldowns(gameTime);
        if (!LightningStrikePolicy.cooldownElapsed(gameTime, nextDimensionStrikeTick)) {
            return false;
        }

        Candidate candidate = selectCandidate(
                level,
                players,
                gameTime,
                Math.max(16, atmosphericCellSize),
                safeSettings
        );
        if (candidate == null
                || level.random.nextDouble()
                >= LightningStrikePolicy.strikeChance(candidate.sample(), safeSettings)) {
            return false;
        }

        // Vanilla's target resolver preserves lightning rods and exposed
        // living-entity targeting. It runs only after a bounded loaded-column
        // candidate and the single per-dimension probability roll succeed.
        BlockPos target = ((ServerLevelLightningAccessor) level)
                .wildernessodysseyapi$findLightningTargetAround(candidate.surface());
        if (!isLoadedAndTicking(level, target)) {
            return false;
        }

        long targetCell = AtmosphereCellKey.fromBlock(
                target.getX(),
                target.getZ(),
                Math.max(16, atmosphericCellSize)
        ).packed();
        if (!LightningStrikePolicy.cooldownElapsed(
                gameTime,
                nextCellStrikeTick.getOrDefault(targetCell, Long.MIN_VALUE)
        ) || !precipitation.lightningEligible(level, target)) {
            return false;
        }

        if (!spawnVanillaLightning(level, target)) {
            return false;
        }

        // Publish only after Minecraft accepts the real lightning entity.
        WorldDisturbanceService.publish(
                level,
                target,
                WorldDisturbanceType.LIGHTNING,
                48,
                null,
                false
        );

        nextDimensionStrikeTick = safeAdd(gameTime, safeSettings.dimensionCooldownTicks());
        rememberCellCooldown(candidate.cellKey(), gameTime, safeSettings.cellCooldownTicks());
        rememberCellCooldown(targetCell, gameTime, safeSettings.cellCooldownTicks());
        return true;
    }

    /** Clears ephemeral cadence state after config reload or level teardown. */
    public void reset() {
        nextCheckTick = Long.MIN_VALUE;
        nextDimensionStrikeTick = Long.MIN_VALUE;
        playerCursor = 0;
        nextCellStrikeTick.clear();
    }

    private Candidate selectCandidate(
            ServerLevel level,
            List<ServerPlayer> players,
            long gameTime,
            int atmosphericCellSize,
            WeatherConfig.LightningSettings settings
    ) {
        int playerCount = players.size();
        int budget = LightningStrikePolicy.candidateBudget(playerCount, settings);
        int start = Math.floorMod(playerCursor, playerCount);
        Set<Long> visitedChunks = new HashSet<>(budget * 2);
        Candidate strongest = null;

        for (int attempt = 0; attempt < budget; attempt++) {
            ServerPlayer player = players.get((start + attempt) % playerCount);
            BlockPos column = randomColumn(level, player.blockPosition(), settings.candidateRadiusBlocks());
            long chunkKey = ChunkPos.asLong(
                    SectionPos.blockToSectionCoord(column.getX()),
                    SectionPos.blockToSectionCoord(column.getZ())
            );
            if (!visitedChunks.add(chunkKey) || !isLoadedAndTicking(level, column)) {
                continue;
            }

            BlockPos surface = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING, column);
            long cellKey = AtmosphereCellKey.fromBlock(
                    surface.getX(),
                    surface.getZ(),
                    atmosphericCellSize
            ).packed();
            if (!LightningStrikePolicy.cooldownElapsed(
                    gameTime,
                    nextCellStrikeTick.getOrDefault(cellKey, Long.MIN_VALUE)
            )) {
                continue;
            }

            WeatherSample sample = weather.sample(level, surface);
            if (!sample.lightningEligible() || !level.canSeeSky(surface.above())) {
                continue;
            }

            if (strongest == null
                    || sample.thunderIntensity() > strongest.sample().thunderIntensity()) {
                strongest = new Candidate(surface.immutable(), sample, cellKey);
            }
        }

        playerCursor = Math.floorMod(start + budget, playerCount);
        return strongest;
    }

    private static List<ServerPlayer> nonSpectatorPlayers(List<ServerPlayer> players) {
        List<ServerPlayer> anchors = new ArrayList<>(players.size());
        for (ServerPlayer player : players) {
            if (!player.isSpectator()) {
                anchors.add(player);
            }
        }
        return anchors;
    }

    private static BlockPos randomColumn(ServerLevel level, BlockPos center, int radius) {
        double angle = level.random.nextDouble() * Math.PI * 2.0;
        double distance = Math.sqrt(level.random.nextDouble()) * radius;
        int x = Mth.floor(center.getX() + 0.5 + Math.cos(angle) * distance);
        int z = Mth.floor(center.getZ() + 0.5 + Math.sin(angle) * distance);
        int y = Mth.clamp(
                center.getY(),
                level.getMinBuildHeight(),
                level.getMaxBuildHeight() - 1
        );
        return new BlockPos(x, y, z);
    }

    private static boolean isLoadedAndTicking(ServerLevel level, BlockPos position) {
        if (position == null || !level.getWorldBorder().isWithinBounds(position)) {
            return false;
        }
        int chunkX = SectionPos.blockToSectionCoord(position.getX());
        int chunkZ = SectionPos.blockToSectionCoord(position.getZ());
        return level.getChunkSource().getChunkNow(chunkX, chunkZ) != null
                && level.isPositionEntityTicking(position);
    }

    private static boolean spawnVanillaLightning(ServerLevel level, BlockPos target) {
        DifficultyInstance difficulty = level.getCurrentDifficultyAt(target);
        boolean skeletonTrap = level.getGameRules().getBoolean(GameRules.RULE_DOMOBSPAWNING)
                && level.random.nextDouble() < difficulty.getEffectiveDifficulty() * 0.01
                && !(level.getBlockState(target.below()).getBlock() instanceof LightningRodBlock);
        if (skeletonTrap) {
            SkeletonHorse horse = EntityType.SKELETON_HORSE.create(level);
            if (horse != null) {
                horse.setTrap(true);
                horse.setAge(0);
                horse.setPos(target.getX(), target.getY(), target.getZ());
                level.addFreshEntity(horse);
            }
        }

        LightningBolt lightning = EntityType.LIGHTNING_BOLT.create(level);
        if (lightning == null) {
            return false;
        }
        lightning.moveTo(Vec3.atBottomCenterOf(target));
        lightning.setVisualOnly(skeletonTrap);
        return level.addFreshEntity(lightning);
    }

    private void pruneExpiredCellCooldowns(long gameTime) {
        nextCellStrikeTick.entrySet().removeIf(entry -> entry.getValue() <= gameTime);
    }

    private void rememberCellCooldown(long cellKey, long gameTime, int cooldownTicks) {
        if (!nextCellStrikeTick.containsKey(cellKey)
                && nextCellStrikeTick.size() >= MAX_TRACKED_CELL_COOLDOWNS) {
            evictEarliestCellCooldown();
        }
        nextCellStrikeTick.put(cellKey, safeAdd(gameTime, cooldownTicks));
    }

    private void evictEarliestCellCooldown() {
        Long earliestKey = null;
        long earliestTick = Long.MAX_VALUE;
        for (Map.Entry<Long, Long> entry : nextCellStrikeTick.entrySet()) {
            if (entry.getValue() < earliestTick) {
                earliestKey = entry.getKey();
                earliestTick = entry.getValue();
            }
        }
        if (earliestKey != null) {
            nextCellStrikeTick.remove(earliestKey);
        }
    }

    private static long safeAdd(long gameTime, int delayTicks) {
        long delay = Math.max(0, delayTicks);
        return gameTime > Long.MAX_VALUE - delay ? Long.MAX_VALUE : gameTime + delay;
    }

    private record Candidate(BlockPos surface, WeatherSample sample, long cellKey) {
    }
}
