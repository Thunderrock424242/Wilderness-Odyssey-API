package com.thunder.wildernessodysseyapi.meteor.event;

import com.thunder.wildernessodysseyapi.meteor.config.MeteorConfig;
import com.thunder.wildernessodysseyapi.meteor.entity.MeteorEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;

import java.util.List;

/**
 * Handles the random scheduling of the meteor impact weather event.
 * <p>
 * Each game tick, the server checks a cooldown timer. When it fires,
 * there is a 1-in-N chance of triggering a meteor shower. On trigger,
 * 2–5 meteors are spawned per player in the overworld, each aimed at
 * a landing spot that:
 *   - Avoids being too close to any player
 *   - Has a bias toward landing near crying obsidian blocks
 */
public class MeteorImpactEvent {

    // Per-dimension tick counter (we use server time via level.getGameTime())
    private static long lastCheckTime = 0;

    @SubscribeEvent
    public static void onLevelTick(LevelTickEvent.Post event) {
        if (!(event.getLevel() instanceof ServerLevel level)) return;
        // Only run in the overworld (dimension key check)
        if (!level.dimension().equals(net.minecraft.world.level.Level.OVERWORLD)) return;

        long gameTime = level.getGameTime();

        int checkInterval = MeteorConfig.EVENT_CHECK_INTERVAL_TICKS.get();
        if (gameTime - lastCheckTime < checkInterval) return;
        lastCheckTime = gameTime;

        // Random chance gate
        int chance = MeteorConfig.EVENT_CHANCE_PER_CHECK.get();
        if (level.random.nextInt(chance) != 0) return;

        // Trigger the event!
        spawnMeteorShower(level, -1);
    }

    /**
     * Public entry point used by both the random scheduler and the /weather meteor command.
     *
     * @param level  the server overworld level
     * @param count  exact number of meteors to spawn, or -1 to use the config min/max range
     */
    public static void spawnMeteorShower(ServerLevel level, int count) {
        List<ServerPlayer> players = level.players();
        if (players.isEmpty()) return;

        if (count == -1) {
            int minMeteors = MeteorConfig.MIN_METEORS.get();
            int maxMeteors = MeteorConfig.MAX_METEORS.get();
            count = minMeteors + level.random.nextInt(Math.max(1, maxMeteors - minMeteors + 1));
        }

        // Resolve crater radius from config
        int craterRadius = resolveCraterRadius(level);

        // Spread meteors among players: each meteor picks a random player as its center
        for (int i = 0; i < count; i++) {
            ServerPlayer targetPlayer = players.get(level.random.nextInt(players.size()));
            spawnMeteor(level, targetPlayer, craterRadius);
        }
    }

    private static void spawnMeteor(ServerLevel level, ServerPlayer nearPlayer, int craterRadius) {
        int spawnRadius = MeteorConfig.SPAWN_RADIUS.get();

        // Try up to 20 times to find a valid landing spot
        Vec3 landingPos = null;
        for (int attempt = 0; attempt < 20; attempt++) {
            Vec3 candidate = pickCandidateLandingPos(level, nearPlayer, spawnRadius);
            if (candidate != null && isValidLandingSpot(level, candidate)) {
                landingPos = candidate;
                break;
            }
        }

        if (landingPos == null) {
            // Fallback: just land somewhere random near the player, not on them
            double angle  = level.random.nextDouble() * Math.PI * 2;
            double dist   = MeteorConfig.PLAYER_AVOID_RADIUS.get() + 20
                    + level.random.nextDouble() * (spawnRadius * 0.5);
            landingPos = new Vec3(
                    nearPlayer.getX() + Math.cos(angle) * dist,
                    nearPlayer.getY(),
                    nearPlayer.getZ() + Math.sin(angle) * dist
            );
        }

        // Find actual ground height at the landing X/Z
        int groundY = findGroundY(level, (int) landingPos.x, (int) landingPos.z);
        landingPos = new Vec3(landingPos.x, groundY, landingPos.z);

        // Spawn point: high up and offset horizontally so it comes in at an angle
        double angleIn = level.random.nextDouble() * Math.PI * 2;
        double horizontalOffset = 80 + level.random.nextDouble() * 60;
        double spawnX = landingPos.x + Math.cos(angleIn) * horizontalOffset;
        double spawnZ = landingPos.z + Math.sin(angleIn) * horizontalOffset;
        double spawnY = Math.max(landingPos.y + 160, level.getMaxBuildHeight() - 10);

        Vec3 spawnPos = new Vec3(spawnX, spawnY, spawnZ);

        MeteorEntity meteor = MeteorEntity.create(level, spawnPos, landingPos, craterRadius);
        level.addFreshEntity(meteor);
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

        // Check for crying obsidian bias: scan a moderate area for crying obsidian
        int searchR = MeteorConfig.CRYING_OBSIDIAN_SEARCH_RADIUS.get();
        BlockPos candidate = new BlockPos((int) cx, (int) nearPlayer.getY(), (int) cz);
        BlockPos nearestCO = findNearestCryingObsidian(level, candidate, searchR);

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
        int avoidRadius = MeteorConfig.PLAYER_AVOID_RADIUS.get();

        // Check player proximity
        List<Player> nearbyPlayers = level.getEntitiesOfClass(
                Player.class,
                new AABB(pos, pos).inflate(avoidRadius)
        );
        if (!nearbyPlayers.isEmpty()) return false;

        // Check build height
        int groundY = findGroundY(level, (int) pos.x, (int) pos.z);
        return groundY > level.getMinBuildHeight() && groundY < level.getMaxBuildHeight() - 20;
    }

    /**
     * Scan for the nearest crying obsidian block within a radius.
     * Uses a spiral outward scan to find the closest one efficiently.
     */
    private static BlockPos findNearestCryingObsidian(ServerLevel level, BlockPos center, int radius) {
        // Scan in horizontal slices, check 3 vertical levels per column for perf
        int closestDistSq = Integer.MAX_VALUE;
        BlockPos closest = null;

        // Step size — for large radii we don't check every block
        int step = Math.max(1, radius / 32);

        for (int x = -radius; x <= radius; x += step) {
            for (int z = -radius; z <= radius; z += step) {
                if (x * x + z * z > radius * radius) continue;
                int bx = center.getX() + x;
                int bz = center.getZ() + z;

                // Check a vertical band around the surface
                int surfaceY = findGroundY(level, bx, bz);
                for (int dy = -4; dy <= 4; dy++) {
                    BlockPos check = new BlockPos(bx, surfaceY + dy, bz);
                    if (level.isOutsideBuildHeight(check)) continue;
                    if (level.getBlockState(check).is(Blocks.CRYING_OBSIDIAN)) {
                        int distSq = check.distManhattan(center);
                        if (distSq < closestDistSq) {
                            closestDistSq = distSq;
                            closest = check;
                        }
                    }
                }
            }
        }

        return closest;
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private static int findGroundY(ServerLevel level, int x, int z) {
        // Walk down from max height to find first solid surface
        int maxY = Math.min(level.getMaxBuildHeight() - 1, 320);
        for (int y = maxY; y > level.getMinBuildHeight(); y--) {
            BlockPos pos = new BlockPos(x, y, z);
            if (!level.getBlockState(pos).isAir() && level.getBlockState(pos).isSolid()) {
                return y + 1;
            }
        }
        return 64; // ocean floor fallback
    }

    private static int resolveCraterRadius(ServerLevel level) {
        MeteorConfig.DestructionLevel preset = MeteorConfig.DESTRUCTION_LEVEL.get();

        if (preset == MeteorConfig.DestructionLevel.CUSTOM) {
            int min = MeteorConfig.CRATER_RADIUS_MIN.get();
            int max = MeteorConfig.CRATER_RADIUS_MAX.get();
            return min + level.random.nextInt(Math.max(1, max - min + 1));
        } else {
            return preset.minRadius + level.random.nextInt(
                    Math.max(1, preset.maxRadius - preset.minRadius + 1));
        }
    }
}