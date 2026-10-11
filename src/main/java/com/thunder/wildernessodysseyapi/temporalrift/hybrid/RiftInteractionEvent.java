package com.thunder.wildernessodysseyapi.temporalrift.hybrid;

import com.thunder.wildernessodysseyapi.temporalrift.blockentity.RiftCoreBlockEntity;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.Event;
import net.neoforged.bus.api.ICancellableEvent;

/** Server game-thread entry hook for stabilization, story and future destinations.
 * Cancel to handle entry instead of the default unstable rejection.
 * STABILIZED emits this hook but has no default travel behavior.
 */
public final class RiftInteractionEvent extends Event implements ICancellableEvent {
    private final ServerPlayer player;
    private final RiftCoreBlockEntity core;
    private final RiftState state;
    private final double speed;

    public RiftInteractionEvent(ServerPlayer player, RiftCoreBlockEntity core, double speed) {
        this.player = player;
        this.core = core;
        this.state = core.getRiftState();
        this.speed = speed;
    }

    public ServerPlayer getPlayer() { return player; }
    public RiftCoreBlockEntity getCore() { return core; }
    public RiftState getState() { return state; }
    public double getEntrySpeed() { return speed; }
}
