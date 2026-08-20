package com.thunder.wildernessodysseyapi.meteor.event;

import com.thunder.wildernessodysseyapi.meteor.config.MeteorConfig;
import com.thunder.wildernessodysseyapi.meteor.entity.MeteorEntity;
import com.thunder.wildernessodysseyapi.meteor.api.MeteorSiteSource;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.level.LevelEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;

import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.OptionalInt;
import java.util.WeakHashMap;

/**
 * Handles the random scheduling of the meteor impact weather event.
 * <p>
 * While players are present in the Overworld, the server checks a configurable
 * rare-event timer. A successful roll spawns the configured meteor count around
 * active players while keeping impact positions outside the safety radius.
 */
public final class MeteorImpactEvent {

    private static final long UNINITIALIZED_CHECK_TIME = Long.MIN_VALUE;
    private static final int MAX_PENDING_METEORS_PER_LEVEL = 64;
    private static final int MAX_LANDING_ATTEMPTS = 12;
    private static final int MAX_RELEASE_RETRIES = 20;
    private static final int MAX_CRYING_OBSIDIAN_COLUMN_SAMPLES = 48;
    private static final int METEORS_RELEASED_PER_LEVEL_TICK = 1;

    // Runtime values are partitioned by logical server. Weak keys are a final
    // safety net if an abnormal shutdown skips the explicit cleanup event.
    private static final Map<MinecraftServer, MeteorRuntimeState> RUNTIME_STATES = new WeakHashMap<>();

    private MeteorImpactEvent() {
    }

    /**
     * Advances the natural meteor timer after each Overworld tick.
     *
     * <p>The timer starts when a player is present and waits a complete interval
     * before its first roll. This prevents server restarts and empty-world uptime
     * from making an intentionally rare event happen more often.</p>
     *
     * @param event the completed level tick supplied by NeoForge
     */
    @SubscribeEvent
    public static void onLevelTick(LevelTickEvent.Post event) {
        if (!(event.getLevel() instanceof ServerLevel level)) {
            return;
        }

        // Multi-meteor requests are released through a strict per-level budget
        // instead of resolving every landing search in one server tick.
        releasePendingMeteors(level);

        if (!level.dimension().equals(Level.OVERWORLD)) {
            return;
        }

        MeteorRuntimeState runtimeState = runtimeState(level.getServer());
        long gameTime = level.getGameTime();
        if (!MeteorConfig.NATURAL_EVENTS_ENABLED.get() || level.players().isEmpty()) {
            // Pausing or disabling natural events resets the cadence anchor so
            // empty-server uptime cannot produce an immediate roll on rejoin.
            runtimeState.lastCheckTime = gameTime;
            return;
        }

        int checkInterval = MeteorConfig.EVENT_CHECK_INTERVAL_TICKS.get();

        // Initialize against current world time so loading or changing worlds
        // never grants an immediate extra rare-event roll.
        if (runtimeState.lastCheckTime == UNINITIALIZED_CHECK_TIME || gameTime < runtimeState.lastCheckTime) {
            runtimeState.lastCheckTime = gameTime;
            return;
        }
        if (gameTime - runtimeState.lastCheckTime < checkInterval) {
            return;
        }
        runtimeState.lastCheckTime = gameTime;

        int chance = MeteorConfig.EVENT_CHANCE_PER_CHECK.get();
        if (level.random.nextInt(chance) != 0) {
            return;
        }

        requestMeteorShower(level, -1, MeteorSiteSource.NATURAL);
    }

    /**
     * Public compatibility entry point for command-driven showers.
     *
     * @param level  the server overworld level
     * @param count  exact number of meteors to spawn, or -1 to use the config min/max range
     */
    public static void spawnMeteorShower(ServerLevel level, int count) {
        requestMeteorShower(level, count, MeteorSiteSource.COMMAND);
    }

    /**
     * Requests a source-classified shower and returns how many meteors the level accepted.
     *
     * <p>Accepted meteors enter a bounded server-owned queue and are released
     * one per level tick. The meteor owner retains count bounds, player safety,
     * landing checks, crater configuration, and successful-impact persistence.</p>
     */
    public static int requestMeteorShower(
            ServerLevel level,
            int count,
            MeteorSiteSource source
    ) {
        List<ServerPlayer> players = level.players();
        if (players.isEmpty()) return 0;

        if (count == -1) {
            count = MeteorConfig.meteorCountRange().randomValue(level.random);
        }
        count = acceptedMeteorCount(count, 0);
        if (count == 0) {
            return 0;
        }

        // Preserve the existing shower behavior where every meteor in one
        // request shares the same resolved crater size.
        int craterRadius = resolveCraterRadius(level);
        ArrayDeque<PendingMeteor> queue = runtimeState(level.getServer()).pendingMeteors
                .computeIfAbsent(level.dimension(), ignored -> new ArrayDeque<>());
        int accepted = acceptedMeteorCount(count, queue.size());

        for (int i = 0; i < accepted; i++) {
            queue.addLast(new PendingMeteor(craterRadius, source, MAX_RELEASE_RETRIES));
        }
        return accepted;
    }

    static int acceptedMeteorCount(int requested, int alreadyQueued) {
        int boundedRequest = Math.max(0, Math.min(20, requested));
        int remainingCapacity = Math.max(0, MAX_PENDING_METEORS_PER_LEVEL - Math.max(0, alreadyQueued));
        return Math.min(boundedRequest, remainingCapacity);
    }

    /** Clears queued work and cadence owned by a stopping server. */
    @SubscribeEvent
    public static void onServerStopping(ServerStoppingEvent event) {
        RUNTIME_STATES.remove(event.getServer());
    }

    /** Drops queued work for a level that can no longer safely accept entities. */
    @SubscribeEvent
    public static void onLevelUnload(LevelEvent.Unload event) {
        if (!(event.getLevel() instanceof ServerLevel level)) {
            return;
        }
        MeteorRuntimeState state = RUNTIME_STATES.get(level.getServer());
        if (state != null) {
            state.pendingMeteors.remove(level.dimension());
            if (level.dimension().equals(Level.OVERWORLD)) {
                state.lastCheckTime = UNINITIALIZED_CHECK_TIME;
            }
        }
    }

    private static void releasePendingMeteors(ServerLevel level) {
        MeteorRuntimeState state = RUNTIME_STATES.get(level.getServer());
        if (state == null) {
            return;
        }
        ArrayDeque<PendingMeteor> queue = state.pendingMeteors.get(level.dimension());
        if (queue == null || queue.isEmpty() || level.players().isEmpty()) {
            return;
        }

        for (int released = 0; released < METEORS_RELEASED_PER_LEVEL_TICK && !queue.isEmpty(); released++) {
            PendingMeteor pending = queue.removeFirst();
            List<ServerPlayer> players = level.players();
            ServerPlayer targetPlayer = players.get(level.random.nextInt(players.size()));
            boolean spawned = spawnMeteor(level, targetPlayer, pending.craterRadius(), pending.source());
            if (!spawned && pending.releaseAttemptsRemaining() > 1) {
                // Loaded terrain can change as players travel. Retrying through
                // the same per-tick budget preserves accepted shower counts
                // without falling back to synchronous chunk generation.
                queue.addLast(new PendingMeteor(
                        pending.craterRadius(),
                        pending.source(),
                        pending.releaseAttemptsRemaining() - 1
                ));
            }
        }
        if (queue.isEmpty()) {
            state.pendingMeteors.remove(level.dimension());
        }
    }

    private static boolean spawnMeteor(
            ServerLevel level,
            ServerPlayer nearPlayer,
            int craterRadius,
            MeteorSiteSource source
    ) {
        int spawnRadius = MeteorConfig.SPAWN_RADIUS.get();

        // A hard attempt budget keeps one queued meteor's work predictable.
        Vec3 landingPos = null;
        for (int attempt = 0; attempt < MAX_LANDING_ATTEMPTS; attempt++) {
            Vec3 candidate = pickCandidateLandingPos(level, nearPlayer, spawnRadius);
            if (candidate != null && isValidLandingSpot(level, candidate)) {
                landingPos = candidate;
                break;
            }
        }

        if (landingPos == null) {
            return false;
        }

        // The candidate was already restricted to a loaded column, so heightmap
        // lookup cannot force a neighboring chunk to load or generate.
        OptionalInt ground = findLoadedGroundY(level, landingPos.x, landingPos.z);
        if (ground.isEmpty()) {
            return false;
        }
        int groundY = ground.getAsInt();
        landingPos = new Vec3(landingPos.x, groundY, landingPos.z);

        // Spawn point: high up and offset horizontally so it comes in at an angle
        double angleIn = level.random.nextDouble() * Math.PI * 2;
        double horizontalOffset = 80 + level.random.nextDouble() * 60;
        double spawnX = landingPos.x + Math.cos(angleIn) * horizontalOffset;
        double spawnZ = landingPos.z + Math.sin(angleIn) * horizontalOffset;
        double spawnY = Math.max(landingPos.y + 160, level.getMaxBuildHeight() - 10);

        // If the cinematic horizontal entry point is outside loaded terrain,
        // spawn vertically above the valid landing column rather than loading a
        // remote chunk merely to add the entity.
        if (!isLoadedColumn(level, spawnX, spawnZ)) {
            spawnX = landingPos.x;
            spawnZ = landingPos.z;
        }

        Vec3 spawnPos = new Vec3(spawnX, spawnY, spawnZ);

        MeteorEntity meteor = MeteorEntity.create(level, spawnPos, landingPos, craterRadius, source);
        return level.addFreshEntity(meteor);
    }

    // -------------------------------------------------------------------------
    // Landing spot selection
    // -------------------------------------------------------------------------

    /**
     * Picks a candidate landing position.
     * Crying obsidian nearby increases the probability of being selected.
     */
    private static Vec3 pickCandidateLandingPos(ServerLevel level, Player nearPlayer, int radius) {
        double angle = level.random.nextDouble() * Math.PI * 2;
        double dist  = level.random.nextDouble() * radius;

        // Base candidate
        double cx = nearPlayer.getX() + Math.cos(angle) * dist;
        double cz = nearPlayer.getZ() + Math.sin(angle) * dist;

        if (!isLoadedColumn(level, cx, cz)) {
            return null;
        }

        // Search a bounded sample of loaded surface columns for the optional
        // crying-obsidian bias. Work no longer grows with the square of radius.
        int searchR = MeteorConfig.CRYING_OBSIDIAN_SEARCH_RADIUS.get();
        BlockPos candidate = BlockPos.containing(cx, nearPlayer.getY(), cz);
        BlockPos nearestCO = findSampledCryingObsidian(level, candidate, searchR, level.random);

        if (nearestCO != null) {
            // 60% chance to bias toward the crying obsidian location
            if (level.random.nextFloat() < 0.60f) {
                // Interpolate candidate toward the crying obsidian
                double bias = 0.4 + level.random.nextDouble() * 0.4; // 40–80% pull
                cx = cx + (nearestCO.getX() - cx) * bias;
                cz = cz + (nearestCO.getZ() - cz) * bias;
            }
        }

        return new Vec3(cx, nearPlayer.getY(), cz);
    }

    /**
     * Returns true if the candidate position is:
     *  - Not too close to any player
     *  - Within world build bounds
     */
    private static boolean isValidLandingSpot(ServerLevel level, Vec3 pos) {
        if (!isLoadedColumn(level, pos.x, pos.z)) {
            return false;
        }
        int avoidRadius = MeteorConfig.PLAYER_AVOID_RADIUS.get();

        // Check player proximity
        List<Player> nearbyPlayers = level.getEntitiesOfClass(
                Player.class,
                new AABB(pos, pos).inflate(avoidRadius)
        );
        if (!nearbyPlayers.isEmpty()) return false;

        // Check build height
        OptionalInt ground = findLoadedGroundY(level, pos.x, pos.z);
        if (ground.isEmpty()) {
            return false;
        }
        int groundY = ground.getAsInt();
        return groundY > level.getMinBuildHeight() && groundY < level.getMaxBuildHeight() - 20;
    }

    /**
     * Samples a fixed number of loaded surface columns for crying obsidian.
     * The fixed budget prevents large configured radii from creating quadratic
     * server-thread work.
     */
    private static BlockPos findSampledCryingObsidian(
            ServerLevel level,
            BlockPos center,
            int radius,
            RandomSource random
    ) {
        double closestDistSq = Double.MAX_VALUE;
        BlockPos closest = null;

        for (int sample = 0; sample < MAX_CRYING_OBSIDIAN_COLUMN_SAMPLES; sample++) {
            double angle = random.nextDouble() * Math.PI * 2.0D;
            double distance = sample == 0 ? 0.0D : Math.sqrt(random.nextDouble()) * radius;
            int bx = center.getX() + (int) Math.round(Math.cos(angle) * distance);
            int bz = center.getZ() + (int) Math.round(Math.sin(angle) * distance);

            OptionalInt surface = findLoadedGroundY(level, bx, bz);
            if (surface.isEmpty()) {
                continue;
            }
            int surfaceY = surface.getAsInt();
            for (int dy = -4; dy <= 4; dy++) {
                BlockPos check = new BlockPos(bx, surfaceY + dy, bz);
                if (level.isOutsideBuildHeight(check)) continue;
                if (level.getBlockState(check).is(Blocks.CRYING_OBSIDIAN)) {
                    double distSq = check.distSqr(center);
                    if (distSq < closestDistSq) {
                        closestDistSq = distSq;
                        closest = check;
                    }
                }
            }
        }

        return closest;
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private static OptionalInt findLoadedGroundY(ServerLevel level, double x, double z) {
        int blockX = (int) Math.floor(x);
        int blockZ = (int) Math.floor(z);
        if (!isLoadedColumn(level, blockX, blockZ)) {
            return OptionalInt.empty();
        }
        return OptionalInt.of(level.getHeight(Heightmap.Types.OCEAN_FLOOR, blockX, blockZ));
    }

    private static OptionalInt findLoadedGroundY(ServerLevel level, int x, int z) {
        if (!isLoadedColumn(level, x, z)) {
            return OptionalInt.empty();
        }
        return OptionalInt.of(level.getHeight(Heightmap.Types.OCEAN_FLOOR, x, z));
    }

    private static boolean isLoadedColumn(ServerLevel level, double x, double z) {
        return isLoadedColumn(level, (int) Math.floor(x), (int) Math.floor(z));
    }

    private static boolean isLoadedColumn(ServerLevel level, int x, int z) {
        return level.hasChunkAt(new BlockPos(x, level.getMinBuildHeight(), z));
    }

    private static int resolveCraterRadius(ServerLevel level) {
        MeteorConfig.DestructionLevel preset = MeteorConfig.DESTRUCTION_LEVEL.get();

        if (preset == MeteorConfig.DestructionLevel.CUSTOM) {
            return MeteorConfig.customCraterRadiusRange().randomValue(level.random);
        } else {
            return preset.minRadius + level.random.nextInt(
                    Math.max(1, preset.maxRadius - preset.minRadius + 1));
        }
    }

    private static MeteorRuntimeState runtimeState(MinecraftServer server) {
        return RUNTIME_STATES.computeIfAbsent(server, ignored -> new MeteorRuntimeState());
    }

    private record PendingMeteor(int craterRadius, MeteorSiteSource source, int releaseAttemptsRemaining) {
    }

    private static final class MeteorRuntimeState {
        private long lastCheckTime = UNINITIALIZED_CHECK_TIME;
        private final Map<ResourceKey<Level>, ArrayDeque<PendingMeteor>> pendingMeteors = new HashMap<>();
    }
}
