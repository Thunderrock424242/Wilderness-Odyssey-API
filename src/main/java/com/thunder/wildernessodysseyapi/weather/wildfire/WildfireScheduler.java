package com.thunder.wildernessodysseyapi.weather.wildfire;

import com.thunder.wildernessodysseyapi.weather.api.AtmosphereCellKey;
import com.thunder.wildernessodysseyapi.weather.api.WeatherQuery;
import com.thunder.wildernessodysseyapi.weather.api.WeatherSample;
import com.thunder.wildernessodysseyapi.weather.config.WeatherConfig;
import com.thunder.wildernessodysseyapi.weather.simulation.AtmosphereEnvironment;
import com.thunder.wildernessodysseyapi.weather.simulation.AtmosphereInputSampler;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.block.BaseFireBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.CampfireBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.Heightmap;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Schedules rare wind-carried embers from unsafe campfires in loaded regions.
 *
 * <p>One instance belongs to one level runtime. It rotates through a strict
 * loaded-chunk budget, rolls at most once per due check, places at most one
 * normal vanilla fire block, and never owns later spread or damage. Minecraft's
 * {@code doFireTick} rule and localized positional rain remain authoritative.</p>
 */
public final class WildfireScheduler {

    private static final int MAXIMUM_TRACKED_CELL_COOLDOWNS = 4_096;
    private static final int MAXIMUM_BLOCK_ENTITIES_PER_CHUNK = 128;
    private static final int MAXIMUM_CAMPFIRES_PER_CHUNK = 8;
    private static final int MINIMUM_EMBER_RANGE = 4;

    private final WeatherQuery weather;
    private final AtmosphereInputSampler inputSampler;
    private final Map<Long, Long> nextCellIgnitionTick = new HashMap<>();

    private long nextCheckTick = Long.MIN_VALUE;
    private long nextDimensionIgnitionTick = Long.MIN_VALUE;
    private int chunkCursor;

    /** Creates a level-owned scheduler backed by authoritative weather and input sampling. */
    public WildfireScheduler(WeatherQuery weather, AtmosphereInputSampler inputSampler) {
        this.weather = Objects.requireNonNull(weather, "weather");
        this.inputSampler = Objects.requireNonNull(inputSampler, "inputSampler");
    }

    /** Performs one due bounded scan and returns whether a vanilla fire was placed. */
    public boolean tick(
            ServerLevel level,
            long gameTime,
            int atmosphericCellSize,
            int environmentResampleIntervalTicks,
            WeatherConfig.WildfireSettings settings
    ) {
        Objects.requireNonNull(level, "level");
        WeatherConfig.WildfireSettings controls = Objects.requireNonNullElse(
                settings,
                WeatherConfig.WildfireSettings.DEFAULT
        );
        if (!controls.enabled() || gameTime < nextCheckTick) {
            return false;
        }
        nextCheckTick = safeAdd(gameTime, controls.checkIntervalTicks());
        if (!fireMayChangeWorld(level)
                || !WildfireIgnitionPolicy.cooldownElapsed(gameTime, nextDimensionIgnitionTick)) {
            return false;
        }

        List<ServerPlayer> players = nonSpectatorPlayers(level.players());
        if (players.isEmpty()) {
            return false;
        }
        pruneExpiredCellCooldowns(gameTime);
        Candidate candidate = selectStrongestCandidate(
                level,
                players,
                gameTime,
                Math.max(16, atmosphericCellSize),
                Math.max(20, environmentResampleIntervalTicks),
                controls
        );
        if (candidate == null
                || level.random.nextDouble()
                >= WildfireIgnitionPolicy.ignitionChance(candidate.risk(), controls)) {
            return false;
        }

        BlockPos ignition = findIgnitionTarget(
                level,
                candidate.campfire(),
                candidate.weather(),
                controls.emberRangeBlocks(),
                controls.targetAttempts()
        );
        if (ignition == null || !placeVanillaFire(level, candidate.campfire(), ignition)) {
            return false;
        }

        nextDimensionIgnitionTick = safeAdd(gameTime, controls.dimensionCooldownTicks());
        rememberCellCooldown(candidate.cellKey(), gameTime, controls.cellCooldownTicks());
        return true;
    }

    /** Evaluates the same risk profile used by live campfire candidates. */
    public WildfireRiskModel.RiskProfile riskAt(
            ServerLevel level,
            BlockPos position,
            int atmosphericCellSize,
            int environmentResampleIntervalTicks
    ) {
        int cellSize = Math.max(16, atmosphericCellSize);
        AtmosphereCellKey cell = AtmosphereCellKey.fromBlock(position.getX(), position.getZ(), cellSize);
        AtmosphereEnvironment environment = inputSampler.sample(
                level,
                cell,
                cellSize,
                Math.max(20, environmentResampleIntervalTicks)
        );
        return WildfireRiskModel.evaluate(weather.sample(level, position), environment);
    }

    /**
     * Forces one nearby campfire ember for operator testing.
     *
     * <p>Climate, probability, and cooldown gates are bypassed. The feature
     * switch, fire gamerule, loaded-chunk, open-campfire, tagged-fuel, and
     * positional-rain safeguards remain active.</p>
     */
    public IgnitionResult forceIgnition(
            ServerLevel level,
            BlockPos origin,
            int atmosphericCellSize,
            WeatherConfig.WildfireSettings settings
    ) {
        WeatherConfig.WildfireSettings controls = Objects.requireNonNullElse(
                settings,
                WeatherConfig.WildfireSettings.DEFAULT
        );
        if (!controls.enabled()) {
            return IgnitionResult.DISABLED;
        }
        if (!fireMayChangeWorld(level)) {
            return IgnitionResult.FIRE_TICK_DISABLED;
        }
        // An operator standing elsewhere receives the nearest loaded source.
        // Supplying a campfire block itself is exact, which also makes focused
        // GameTests immune to neighboring test structures running in parallel.
        BlockState originState = level.getBlockState(origin);
        boolean originIsCampfire = originState.is(Blocks.CAMPFIRE)
                || originState.is(Blocks.SOUL_CAMPFIRE);
        BlockPos campfire = originIsCampfire
                ? (isUnsafeCampfire(level, origin) ? origin.immutable() : null)
                : findNearestCampfire(level, origin, 2);
        if (campfire == null) {
            return IgnitionResult.NO_CAMPFIRE;
        }
        WeatherSample sample = weather.sample(level, campfire);
        BlockPos ignition = findIgnitionTarget(
                level,
                campfire,
                sample,
                controls.emberRangeBlocks(),
                Math.max(controls.targetAttempts(), 24)
        );
        if (ignition == null || !placeVanillaFire(level, campfire, ignition)) {
            return IgnitionResult.NO_FUEL;
        }
        long gameTime = level.getGameTime();
        nextDimensionIgnitionTick = safeAdd(gameTime, controls.dimensionCooldownTicks());
        long cellKey = AtmosphereCellKey.fromBlock(
                campfire.getX(),
                campfire.getZ(),
                Math.max(16, atmosphericCellSize)
        ).packed();
        rememberCellCooldown(cellKey, gameTime, controls.cellCooldownTicks());
        return IgnitionResult.IGNITED;
    }

    /** Clears ephemeral cadence and cooldown state after config reload or level teardown. */
    public void reset() {
        nextCheckTick = Long.MIN_VALUE;
        nextDimensionIgnitionTick = Long.MIN_VALUE;
        chunkCursor = 0;
        nextCellIgnitionTick.clear();
    }

    private Candidate selectStrongestCandidate(
            ServerLevel level,
            List<ServerPlayer> players,
            long gameTime,
            int atmosphericCellSize,
            int environmentResampleIntervalTicks,
            WeatherConfig.WildfireSettings settings
    ) {
        int radius = settings.candidateChunkRadius();
        int width = radius * 2 + 1;
        int chunksInSquare = width * width;
        int budget = WildfireIgnitionPolicy.candidateChunkBudget(players.size(), settings);
        int start = Math.floorMod(chunkCursor, chunksInSquare);
        int inspectedChunks = 0;
        Set<Long> visitedChunks = new HashSet<>(budget * 2);
        Candidate strongest = null;

        for (int playerIndex = 0; playerIndex < players.size() && inspectedChunks < budget; playerIndex++) {
            ServerPlayer player = players.get(playerIndex);
            ChunkPos playerChunk = player.chunkPosition();
            for (int attempt = 0;
                 attempt < settings.candidateChunksPerPlayer() && inspectedChunks < budget;
                 attempt++) {
                int offsetIndex = Math.floorMod(
                        start + playerIndex * settings.candidateChunksPerPlayer() + attempt,
                        chunksInSquare
                );
                int chunkX = playerChunk.x + offsetIndex % width - radius;
                int chunkZ = playerChunk.z + offsetIndex / width - radius;
                long chunkKey = ChunkPos.asLong(chunkX, chunkZ);
                if (!visitedChunks.add(chunkKey)) {
                    continue;
                }
                inspectedChunks++;
                LevelChunk chunk = level.getChunkSource().getChunkNow(chunkX, chunkZ);
                if (chunk == null) {
                    continue;
                }
                Candidate chunkCandidate = inspectChunk(
                        level,
                        chunk,
                        gameTime,
                        atmosphericCellSize,
                        environmentResampleIntervalTicks
                );
                if (chunkCandidate != null
                        && (strongest == null || chunkCandidate.risk().risk() > strongest.risk().risk())) {
                    strongest = chunkCandidate;
                }
            }
        }
        chunkCursor = Math.floorMod(start + settings.candidateChunksPerPlayer(), chunksInSquare);
        return strongest;
    }

    private Candidate inspectChunk(
            ServerLevel level,
            LevelChunk chunk,
            long gameTime,
            int atmosphericCellSize,
            int environmentResampleIntervalTicks
    ) {
        Candidate strongest = null;
        int inspectedBlockEntities = 0;
        int inspectedCampfires = 0;
        for (BlockEntity blockEntity : chunk.getBlockEntities().values()) {
            if (inspectedBlockEntities++ >= MAXIMUM_BLOCK_ENTITIES_PER_CHUNK) {
                break;
            }
            if (!(blockEntity instanceof CampfireBlockEntity)
                    || inspectedCampfires++ >= MAXIMUM_CAMPFIRES_PER_CHUNK) {
                continue;
            }
            BlockPos campfire = blockEntity.getBlockPos();
            if (!isUnsafeCampfire(level, campfire)) {
                continue;
            }
            long cellKey = AtmosphereCellKey.fromBlock(
                    campfire.getX(), campfire.getZ(), atmosphericCellSize
            ).packed();
            if (!WildfireIgnitionPolicy.cooldownElapsed(
                    gameTime,
                    nextCellIgnitionTick.getOrDefault(cellKey, Long.MIN_VALUE)
            )) {
                continue;
            }
            WildfireRiskModel.RiskProfile risk = riskAt(
                    level,
                    campfire,
                    atmosphericCellSize,
                    environmentResampleIntervalTicks
            );
            if (!risk.eligible()) {
                continue;
            }
            WeatherSample sample = weather.sample(level, campfire);
            Candidate candidate = new Candidate(campfire.immutable(), sample, risk, cellKey);
            if (strongest == null || candidate.risk().risk() > strongest.risk().risk()) {
                strongest = candidate;
            }
        }
        return strongest;
    }

    private BlockPos findNearestCampfire(ServerLevel level, BlockPos origin, int chunkRadius) {
        ChunkPos center = new ChunkPos(origin);
        BlockPos nearest = null;
        double nearestDistance = Double.MAX_VALUE;
        for (int dz = -chunkRadius; dz <= chunkRadius; dz++) {
            for (int dx = -chunkRadius; dx <= chunkRadius; dx++) {
                LevelChunk chunk = level.getChunkSource().getChunkNow(center.x + dx, center.z + dz);
                if (chunk == null) {
                    continue;
                }
                int inspected = 0;
                for (BlockEntity blockEntity : chunk.getBlockEntities().values()) {
                    if (inspected++ >= MAXIMUM_BLOCK_ENTITIES_PER_CHUNK) {
                        break;
                    }
                    BlockPos position = blockEntity.getBlockPos();
                    if (!(blockEntity instanceof CampfireBlockEntity) || !isUnsafeCampfire(level, position)) {
                        continue;
                    }
                    double distance = position.distSqr(origin);
                    if (distance <= 32.0 * 32.0 && distance < nearestDistance) {
                        nearest = position.immutable();
                        nearestDistance = distance;
                    }
                }
            }
        }
        return nearest;
    }

    private BlockPos findIgnitionTarget(
            ServerLevel level,
            BlockPos campfire,
            WeatherSample sample,
            int maximumRange,
            int attempts
    ) {
        double windX = sample.wind().x();
        double windZ = sample.wind().z();
        double baseAngle = Math.hypot(windX, windZ) >= 0.05
                ? Math.atan2(windZ, windX)
                : level.random.nextDouble() * Math.PI * 2.0;
        int safeRange = Math.max(MINIMUM_EMBER_RANGE, maximumRange);
        for (int attempt = 0; attempt < attempts; attempt++) {
            double angle = baseAngle + (level.random.nextDouble() - 0.5) * Math.PI * 0.75;
            double distance = MINIMUM_EMBER_RANGE
                    + level.random.nextDouble() * (safeRange - MINIMUM_EMBER_RANGE);
            int x = (int) Math.round(campfire.getX() + Math.cos(angle) * distance);
            int z = (int) Math.round(campfire.getZ() + Math.sin(angle) * distance);
            BlockPos column = new BlockPos(x, campfire.getY(), z);
            if (!isLoadedAndTicking(level, column)) {
                continue;
            }
            BlockPos ignition = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING, column);
            if (Math.abs(ignition.getY() - campfire.getY()) > 16
                    || !isLoadedAndTicking(level, ignition)
                    || !level.canSeeSky(ignition)
                    || weather.isRainingAt(level, ignition)) {
                continue;
            }
            BlockState fuel = level.getBlockState(ignition.below());
            if (!level.getBlockState(ignition).isAir()
                    || !fuel.is(WildfireTags.IGNITION_FUELS)
                    || !BaseFireBlock.canBePlacedAt(level, ignition, Direction.UP)) {
                continue;
            }
            return ignition.immutable();
        }
        return null;
    }

    private static boolean placeVanillaFire(ServerLevel level, BlockPos campfire, BlockPos ignition) {
        if (!level.getBlockState(ignition).isAir()
                || !BaseFireBlock.canBePlacedAt(level, ignition, Direction.UP)) {
            return false;
        }
        if (!level.setBlockAndUpdate(ignition, BaseFireBlock.getState(level, ignition))) {
            return false;
        }
        level.sendParticles(
                ParticleTypes.SMALL_FLAME,
                campfire.getX() + 0.5,
                campfire.getY() + 1.15,
                campfire.getZ() + 0.5,
                6,
                0.28,
                0.18,
                0.28,
                0.025
        );
        level.sendParticles(
                ParticleTypes.LARGE_SMOKE,
                ignition.getX() + 0.5,
                ignition.getY() + 0.35,
                ignition.getZ() + 0.5,
                4,
                0.20,
                0.25,
                0.20,
                0.01
        );
        return true;
    }

    private static boolean isUnsafeCampfire(ServerLevel level, BlockPos position) {
        if (!isLoadedAndTicking(level, position) || !level.canSeeSky(position.above())) {
            return false;
        }
        BlockState state = level.getBlockState(position);
        return state.is(Blocks.CAMPFIRE) && CampfireBlock.isLitCampfire(state);
    }

    private static boolean fireMayChangeWorld(ServerLevel level) {
        return level.dimensionType().hasSkyLight()
                && level.getGameRules().getBoolean(GameRules.RULE_DOFIRETICK);
    }

    private static boolean isLoadedAndTicking(ServerLevel level, BlockPos position) {
        return position != null
                && level.getWorldBorder().isWithinBounds(position)
                && level.getChunkSource().getChunkNow(position.getX() >> 4, position.getZ() >> 4) != null
                && level.isPositionEntityTicking(position);
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

    private void pruneExpiredCellCooldowns(long gameTime) {
        nextCellIgnitionTick.entrySet().removeIf(entry -> entry.getValue() <= gameTime);
    }

    private void rememberCellCooldown(long cellKey, long gameTime, int cooldownTicks) {
        if (!nextCellIgnitionTick.containsKey(cellKey)
                && nextCellIgnitionTick.size() >= MAXIMUM_TRACKED_CELL_COOLDOWNS) {
            evictEarliestCellCooldown();
        }
        nextCellIgnitionTick.put(cellKey, safeAdd(gameTime, cooldownTicks));
    }

    private void evictEarliestCellCooldown() {
        Long earliestKey = null;
        long earliestTick = Long.MAX_VALUE;
        for (Map.Entry<Long, Long> entry : nextCellIgnitionTick.entrySet()) {
            if (entry.getValue() < earliestTick) {
                earliestKey = entry.getKey();
                earliestTick = entry.getValue();
            }
        }
        if (earliestKey != null) {
            nextCellIgnitionTick.remove(earliestKey);
        }
    }

    private static long safeAdd(long gameTime, int delayTicks) {
        long delay = Math.max(0, delayTicks);
        return gameTime > Long.MAX_VALUE - delay ? Long.MAX_VALUE : gameTime + delay;
    }

    /** Operator-facing result for a forced loaded-world ignition attempt. */
    public enum IgnitionResult {
        IGNITED,
        DISABLED,
        FIRE_TICK_DISABLED,
        NO_CAMPFIRE,
        NO_FUEL
    }

    private record Candidate(
            BlockPos campfire,
            WeatherSample weather,
            WildfireRiskModel.RiskProfile risk,
            long cellKey
    ) {
    }
}
