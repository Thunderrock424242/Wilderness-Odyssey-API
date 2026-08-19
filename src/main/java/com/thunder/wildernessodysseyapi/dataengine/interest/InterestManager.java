package com.thunder.wildernessodysseyapi.dataengine.interest;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

/**
 * SERVER THREAD ONLY. Chunk-bucket spatial index and explicit-interest registry.
 *
 * <p>Player locations are refreshed once per server tick. Region dispatch then
 * visits only buckets intersecting the feature radius instead of comparing
 * every world object against every connected player.</p>
 */
public final class InterestManager {
    private static final int BUCKET_SIZE_CHUNKS = 8;

    private final Map<UUID, PlayerInterestContext> contexts = new HashMap<>();
    private final Map<ResourceLocation, Map<Long, List<PlayerInterestContext>>> bucketsByDimension = new HashMap<>();
    private final Map<ResourceLocation, Set<UUID>> featureSubscribers = new HashMap<>();
    private final Map<ResourceLocation, Integer> dimensionPlayerCounts = new HashMap<>();

    /** Refreshes only connected-player positions and removes disconnected contexts. */
    public void refresh(MinecraftServer server, long currentTick) {
        Objects.requireNonNull(server, "Minecraft server is required");
        dimensionPlayerCounts.clear();
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            refreshPlayer(player, currentTick);
            dimensionPlayerCounts.merge(player.level().dimension().location(), 1, Integer::sum);
        }

        Iterator<Map.Entry<UUID, PlayerInterestContext>> iterator = contexts.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<UUID, PlayerInterestContext> entry = iterator.next();
            PlayerInterestContext context = entry.getValue();
            if (context.lastSeenTick() == currentTick) {
                continue;
            }
            removeFromBucket(context);
            for (Set<UUID> subscribers : featureSubscribers.values()) {
                subscribers.remove(entry.getKey());
            }
            iterator.remove();
        }
        featureSubscribers.entrySet().removeIf(entry -> entry.getValue().isEmpty());
    }

    /** Classifies one player without consulting or mutating the spatial index. */
    public InterestTier classify(ServerPlayer player, InterestRegion region, InterestProfile profile) {
        return classify(
                player.level().dimension().location(),
                player.chunkPosition().x,
                player.chunkPosition().z,
                region,
                profile
        );
    }

    /** Pure classification helper used by tests and non-player snapshots. */
    public static InterestTier classify(
            ResourceLocation playerDimension,
            int playerChunkX,
            int playerChunkZ,
            InterestRegion region,
            InterestProfile profile
    ) {
        Objects.requireNonNull(playerDimension, "Player dimension is required");
        Objects.requireNonNull(region, "Interest region is required");
        Objects.requireNonNull(profile, "Interest profile is required");
        if (!playerDimension.equals(region.dimension())) {
            return InterestTier.NONE;
        }
        long distance = Math.max(
                Math.abs((long) playerChunkX - region.chunkX()),
                Math.abs((long) playerChunkZ - region.chunkZ())
        );
        if (distance <= profile.nearRadiusChunks()) {
            return InterestTier.NEAR;
        }
        if (distance <= profile.regionalRadiusChunks()) {
            return InterestTier.REGIONAL;
        }
        if (distance <= profile.distantRadiusChunks()) {
            return InterestTier.DISTANT;
        }
        return InterestTier.NONE;
    }

    /** Convenience check for one feature radius. */
    public boolean isInterested(ServerPlayer player, InterestRegion region, int radiusChunks) {
        return classify(player, region, InterestProfile.within(radiusChunks)) != InterestTier.NONE;
    }

    /**
     * Visits only spatially relevant players and reports how many same-dimension
     * players were filtered out.
     */
    public InterestDispatchResult forEachInterested(
            InterestRegion region,
            InterestProfile profile,
            BiConsumer<ServerPlayer, InterestTier> consumer
    ) {
        Objects.requireNonNull(region, "Interest region is required");
        Objects.requireNonNull(profile, "Interest profile is required");
        Objects.requireNonNull(consumer, "Interest consumer is required");
        int radius = profile.distantRadiusChunks();
        int minimumBucketX = bucketCoordinate((long) region.chunkX() - radius);
        int maximumBucketX = bucketCoordinate((long) region.chunkX() + radius);
        int minimumBucketZ = bucketCoordinate((long) region.chunkZ() - radius);
        int maximumBucketZ = bucketCoordinate((long) region.chunkZ() + radius);
        int interested = 0;
        Map<Long, List<PlayerInterestContext>> dimensionBuckets = bucketsByDimension.get(region.dimension());
        if (dimensionBuckets != null) {
            long width = (long) maximumBucketX - minimumBucketX + 1L;
            long height = (long) maximumBucketZ - minimumBucketZ + 1L;
            long gridArea = width * height;
            if (gridArea <= Math.max(16L, dimensionBuckets.size() * 4L)) {
                for (int bucketX = minimumBucketX; ; bucketX++) {
                    for (int bucketZ = minimumBucketZ; ; bucketZ++) {
                        interested += visitBucket(
                                dimensionBuckets.get(packBucket(bucketX, bucketZ)), region, profile, consumer
                        );
                        if (bucketZ == maximumBucketZ) {
                            break;
                        }
                    }
                    if (bucketX == maximumBucketX) {
                        break;
                    }
                }
            } else {
                // Very large feature radii should inspect only non-empty buckets
                // rather than allocating/probing millions of empty grid keys.
                for (Map.Entry<Long, List<PlayerInterestContext>> bucketEntry : dimensionBuckets.entrySet()) {
                    int bucketX = unpackBucketX(bucketEntry.getKey());
                    int bucketZ = unpackBucketZ(bucketEntry.getKey());
                    if (bucketX >= minimumBucketX && bucketX <= maximumBucketX
                            && bucketZ >= minimumBucketZ && bucketZ <= maximumBucketZ) {
                        interested += visitBucket(bucketEntry.getValue(), region, profile, consumer);
                    }
                }
            }
        }

        int dimensionPlayers = dimensionPlayerCounts.getOrDefault(region.dimension(), 0);
        return new InterestDispatchResult(interested, Math.max(0, dimensionPlayers - interested));
    }

    /** Updates an explicit opt-in such as an actively viewed debug page. */
    public void setFeatureInterest(
            ServerPlayer player,
            ResourceLocation featureId,
            boolean interested,
            long currentTick
    ) {
        Objects.requireNonNull(player, "Server player is required");
        Objects.requireNonNull(featureId, "Feature id is required");
        PlayerInterestContext context = refreshPlayer(player, currentTick);
        if (!context.setSubscribed(featureId, interested)) {
            return;
        }
        Set<UUID> subscribers = featureSubscribers.computeIfAbsent(featureId, ignored -> new HashSet<>());
        if (interested) {
            subscribers.add(player.getUUID());
        } else {
            subscribers.remove(player.getUUID());
            if (subscribers.isEmpty()) {
                featureSubscribers.remove(featureId);
            }
        }
    }

    /** Visits players explicitly interested in a non-spatial feature. */
    public InterestDispatchResult forEachFeatureInterested(
            ResourceLocation featureId,
            Consumer<ServerPlayer> consumer
    ) {
        Objects.requireNonNull(featureId, "Feature id is required");
        Objects.requireNonNull(consumer, "Feature consumer is required");
        Set<UUID> subscribers = featureSubscribers.get(featureId);
        if (subscribers == null || subscribers.isEmpty()) {
            return new InterestDispatchResult(0, contexts.size());
        }
        int interested = 0;
        for (UUID playerId : subscribers) {
            PlayerInterestContext context = contexts.get(playerId);
            if (context != null) {
                consumer.accept(context.player());
                interested++;
            }
        }
        return new InterestDispatchResult(interested, Math.max(0, contexts.size() - interested));
    }

    public int trackedPlayers() {
        return contexts.size();
    }

    public void clear() {
        contexts.clear();
        bucketsByDimension.clear();
        featureSubscribers.clear();
        dimensionPlayerCounts.clear();
    }

    private PlayerInterestContext refreshPlayer(ServerPlayer player, long currentTick) {
        PlayerInterestContext context = contexts.get(player.getUUID());
        if (context == null) {
            context = new PlayerInterestContext(player, currentTick);
            contexts.put(player.getUUID(), context);
            addToBucket(context);
            return context;
        }

        ResourceLocation previousDimension = context.dimension();
        long previousBucket = bucketKey(context.chunkX(), context.chunkZ());
        ResourceLocation newDimension = player.level().dimension().location();
        int newChunkX = player.chunkPosition().x;
        int newChunkZ = player.chunkPosition().z;
        long newBucket = bucketKey(newChunkX, newChunkZ);
        if (!previousDimension.equals(newDimension) || previousBucket != newBucket) {
            removeFromBucket(context);
            context.update(player, currentTick);
            addToBucket(context);
        } else {
            context.update(player, currentTick);
        }
        return context;
    }

    private void addToBucket(PlayerInterestContext context) {
        bucketsByDimension
                .computeIfAbsent(context.dimension(), ignored -> new HashMap<>())
                .computeIfAbsent(bucketKey(context.chunkX(), context.chunkZ()), ignored -> new ArrayList<>())
                .add(context);
    }

    private void removeFromBucket(PlayerInterestContext context) {
        Map<Long, List<PlayerInterestContext>> dimensionBuckets = bucketsByDimension.get(context.dimension());
        if (dimensionBuckets == null) {
            return;
        }
        long key = bucketKey(context.chunkX(), context.chunkZ());
        List<PlayerInterestContext> bucket = dimensionBuckets.get(key);
        if (bucket == null) {
            return;
        }
        bucket.remove(context);
        if (bucket.isEmpty()) {
            dimensionBuckets.remove(key);
            if (dimensionBuckets.isEmpty()) {
                bucketsByDimension.remove(context.dimension());
            }
        }
    }

    private static int visitBucket(
            List<PlayerInterestContext> bucket,
            InterestRegion region,
            InterestProfile profile,
            BiConsumer<ServerPlayer, InterestTier> consumer
    ) {
        if (bucket == null) {
            return 0;
        }
        int interested = 0;
        for (PlayerInterestContext context : bucket) {
            InterestTier tier = classify(context.dimension(), context.chunkX(), context.chunkZ(), region, profile);
            if (tier != InterestTier.NONE) {
                consumer.accept(context.player(), tier);
                interested++;
            }
        }
        return interested;
    }

    private static long bucketKey(int chunkX, int chunkZ) {
        return packBucket(Math.floorDiv(chunkX, BUCKET_SIZE_CHUNKS), Math.floorDiv(chunkZ, BUCKET_SIZE_CHUNKS));
    }

    private static long packBucket(int bucketX, int bucketZ) {
        return ((long) bucketX & 0xFFFF_FFFFL) | ((long) bucketZ << 32);
    }

    private static int unpackBucketX(long key) {
        return (int) key;
    }

    private static int unpackBucketZ(long key) {
        return (int) (key >> 32);
    }

    private static int bucketCoordinate(long chunkCoordinate) {
        long bucket = Math.floorDiv(chunkCoordinate, BUCKET_SIZE_CHUNKS);
        return (int) Math.max(Integer.MIN_VALUE, Math.min(Integer.MAX_VALUE, bucket));
    }

    /** Counts delivered and filtered players without allocating a recipient list. */
    public record InterestDispatchResult(int interestedPlayers, int filteredPlayers) {
    }
}
