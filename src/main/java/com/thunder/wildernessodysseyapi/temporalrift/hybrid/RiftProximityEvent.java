package com.thunder.wildernessodysseyapi.temporalrift.hybrid;

import com.thunder.wildernessodysseyapi.temporalrift.blockentity.RiftCoreBlockEntity;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.Event;

/** Aether/research/weather hook, at most once per player/core per 10 seconds within 10 blocks. */
public final class RiftProximityEvent extends Event {
    private final ServerPlayer player;
    private final RiftCoreBlockEntity core;
    private final double intensity;

    public RiftProximityEvent(ServerPlayer player, RiftCoreBlockEntity core, double intensity) {
        this.player = player;
        this.core = core;
        this.intensity = intensity;
    }

    public ServerPlayer getPlayer() { return player; }
    public RiftCoreBlockEntity getCore() { return core; }
    public double getIntensity() { return intensity; }
}
