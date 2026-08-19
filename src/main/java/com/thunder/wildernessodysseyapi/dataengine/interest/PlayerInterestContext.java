package com.thunder.wildernessodysseyapi.dataengine.interest;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * SERVER THREAD ONLY. Last indexed location and explicit feature subscriptions
 * for one connected player.
 */
public final class PlayerInterestContext {
    private final UUID playerId;
    private final Set<ResourceLocation> featureSubscriptions = new HashSet<>();

    private ServerPlayer player;
    private ResourceLocation dimension;
    private int chunkX;
    private int chunkZ;
    private long lastSeenTick;

    PlayerInterestContext(ServerPlayer player, long currentTick) {
        this.playerId = player.getUUID();
        update(player, currentTick);
    }

    public UUID playerId() {
        return playerId;
    }

    public ResourceLocation dimension() {
        return dimension;
    }

    public int chunkX() {
        return chunkX;
    }

    public int chunkZ() {
        return chunkZ;
    }

    public boolean isSubscribed(ResourceLocation featureId) {
        return featureSubscriptions.contains(featureId);
    }

    ServerPlayer player() {
        return player;
    }

    long lastSeenTick() {
        return lastSeenTick;
    }

    void update(ServerPlayer player, long currentTick) {
        this.player = player;
        this.dimension = player.level().dimension().location();
        this.chunkX = player.chunkPosition().x;
        this.chunkZ = player.chunkPosition().z;
        this.lastSeenTick = currentTick;
    }

    boolean setSubscribed(ResourceLocation featureId, boolean subscribed) {
        return subscribed
                ? featureSubscriptions.add(featureId)
                : featureSubscriptions.remove(featureId);
    }
}
