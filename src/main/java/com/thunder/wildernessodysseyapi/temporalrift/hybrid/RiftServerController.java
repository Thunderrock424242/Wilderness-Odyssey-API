package com.thunder.wildernessodysseyapi.temporalrift.hybrid;

import com.thunder.wildernessodysseyapi.temporalrift.blockentity.RiftCoreBlockEntity;
import com.thunder.wildernessodysseyapi.temporalrift.config.TemporalRiftConfig;
import net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.level.entity.EntityTypeTest;
import net.minecraft.util.AbortableIterationConsumer.Continuation;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Loaded-core server authority. Player history dies with the core/chunk; no global world references. */
public final class RiftServerController {
    private static final int MAX_TRACKED_PLAYERS = 256;
    private static final int MAX_ITEMS = 32;
    private static final String LAST_FORCE = "wo_rift_force_tick";
    private static final String LAST_ENTRY = "wo_rift_entry_tick";
    private final Map<UUID, PlayerSample> players = new HashMap<>();
    private record PlayerSample(Vec3 feet, long tick, long lastWarning) { }

    public void clear() { players.clear(); }

    public void tick(ServerLevel level, RiftCoreBlockEntity core) {
        if (core.getManagedCloseTick() > 0 && level.getGameTime() % 20 == 0) {
            var managed = com.thunder.wildernessodysseyapi.temporalrift.TemporalRiftSavedData.get(level.getServer());
            if (level.getGameTime() >= core.getManagedCloseTick() || !managed.isRiftOpen()
                    || !core.getBlockPos().equals(managed.getRiftPosition())) {
                level.removeBlock(core.getBlockPos(), false);
                return;
            }
        }
        if (!TemporalRiftConfig.ENABLE_RIFT_SYSTEM.get()) { clear(); return; }
        long tick = level.getGameTime();
        Vec3 center = RiftTrigger.center(core.getBlockPos());
        double radius = RiftConfig.FIELD_RADIUS.get();
        AABB trigger = RiftTrigger.bounds(core.getBlockPos(), core.getFacing());
        boolean nearPlayer = false;
        for (ServerPlayer player : level.players()) {
            if (player.isSpectator()) continue;
            Vec3 feet = player.position();
            PlayerSample old = players.get(player.getUUID());
            // Do not bridge travel, respawn or stale positions across a chunk unload.
            Vec3 previous = old != null && old.tick == tick - 1 && old.feet.distanceToSqr(feet) < 64 * 64
                    ? old.feet : new Vec3(player.xo, player.yo, player.zo);
            if (previous.distanceToSqr(feet) > 64 * 64) previous = feet;
            double distanceSquared = feet.distanceToSqr(center);
            if (distanceSquared > (radius + 4) * (radius + 4)
                    && previous.distanceToSqr(center) > (radius + 4) * (radius + 4)
                    && !RiftTrigger.crosses(new AABB(center, center).inflate(radius + 4), previous, feet, 0, 0)) continue;
            nearPlayer = true;
            if (old != null || players.size() < MAX_TRACKED_PLAYERS) {
                long warning = old == null ? tick - 200 : old.lastWarning;
                if (distanceSquared <= 100 && tick - warning >= 200 && core.getRiftState().rejectsEntry()) {
                    warning = tick;
                    NeoForge.EVENT_BUS.post(new RiftProximityEvent(player, core,
                            RiftFieldMath.intensity(Math.sqrt(distanceSquared), radius)));
                }
                players.put(player.getUUID(), new PlayerSample(feet, tick, warning));
            }
            if (tick % 10 == 0) sendField(level, core, player, false);
            if (!player.isCreative() && RiftTrigger.crosses(trigger, previous, feet, player.getBbWidth() / 2, player.getBbHeight())) {
                enter(level, core, player, previous, tick);
            } else if (!player.isCreative() && tick % 5 == 0 && distanceSquared < 100 && !recentEntry(player, tick)) {
                applyForce(player, center, core.getRiftState(), tick, core.getBlockPos().asLong());
            }
        }
        if (tick % 20 == 0) players.values().removeIf(sample -> tick - sample.tick > 20);
        if (nearPlayer && tick % 5 == 0 && core.getRiftState().forceMultiplier() > 0 && RiftConfig.ITEM_FORCE.get() > 0
                && TemporalRiftConfig.ENABLE_RIFT_PULL_EFFECT.get()) {
            int[] visited = {0};
            // Abort the spatial query itself, rather than collecting every item and truncating afterward.
            level.getEntities().get(EntityTypeTest.forClass(ItemEntity.class), new AABB(center, center).inflate(10), item -> {
                if (item.isAlive() && item.getPersistentData().getLong(LAST_FORCE) != tick) {
                    item.getPersistentData().putLong(LAST_FORCE, tick);
                    Vec3 delta = center.subtract(item.position());
                    double strength = RiftConfig.ITEM_FORCE.get() * core.getRiftState().forceMultiplier()
                            * RiftFieldMath.intensity(delta.length(), 10) * 0.7;
                    Vec3 acceleration = delta.normalize().scale(strength);
                    if (item.getDeltaMovement().lengthSqr() < 0.25) item.push(acceleration.x, acceleration.y * 0.2 + strength * 0.35, acceleration.z);
                }
                return ++visited[0] >= MAX_ITEMS ? Continuation.ABORT : Continuation.CONTINUE;
            });
        }
    }

    private static boolean recentEntry(ServerPlayer player, long tick) {
        var tag = player.getPersistentData();
        long last = tag.getLong(LAST_ENTRY);
        return tag.contains(LAST_ENTRY) && tick >= last && tick - last < 30;
    }

    static void enter(ServerLevel level, RiftCoreBlockEntity core, ServerPlayer player, Vec3 previous, long tick) {
        if (recentEntry(player, tick)) return;
        player.getPersistentData().putLong(LAST_ENTRY, tick);
        double speed = player.getDeltaMovement().length();
        RiftInteractionEvent event = new RiftInteractionEvent(player, core, speed);
        NeoForge.EVENT_BUS.post(event);
        if (event.isCanceled() || !event.getState().rejectsEntry()) return;
        sendField(level, core, player, true);
        Vec3 center = RiftTrigger.center(core.getBlockPos());
        var facing = core.getFacing();
        Vec3 normal = new Vec3(facing.getStepX(), 0, facing.getStepZ());
        double side = previous.subtract(center).dot(normal);
        if (Math.abs(side) < 0.05) side = -player.getDeltaMovement().dot(normal);
        Vec3 away = normal.scale(side < 0 ? -1 : 1);
        float damage = RiftFieldMath.impactDamage(speed, player.getHealth(), RiftConfig.BASE_DAMAGE.get(),
                RiftConfig.VELOCITY_DAMAGE.get(), RiftConfig.MAX_DAMAGE.get());
        if (damage > 0) player.hurt(player.damageSources().magic(), damage);
        // Replace momentum rather than adding to high-speed Elytra velocity.
        player.setDeltaMovement(away.scale(0.55 + Math.min(0.25, speed * 0.08)).add(0, 0.16, 0));
        player.fallDistance = 0;
        player.hurtMarked = true;
        player.connection.send(new ClientboundSetEntityMotionPacket(player));
        if (RiftConfig.ENTRY_STATUS.get()) player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 40, 0));
    }

    private static void applyForce(ServerPlayer player, Vec3 center, RiftState state, long tick, long pulseOffset) {
        if (!TemporalRiftConfig.ENABLE_RIFT_PULL_EFFECT.get() || state.forceMultiplier() == 0
                || RiftConfig.PLAYER_FORCE.get() == 0 || player.getPersistentData().getLong(LAST_FORCE) == tick) return;
        player.getPersistentData().putLong(LAST_FORCE, tick);
        Vec3 delta = center.subtract(player.position());
        double strength = RiftConfig.PLAYER_FORCE.get() * state.forceMultiplier() * RiftFieldMath.intensity(delta.length(), 10)
                * (0.3 + 0.7 * RiftFieldMath.pulse(tick, pulseOffset));
        Vec3 acceleration = delta.normalize().scale(strength);
        player.push(acceleration.x, Math.clamp(acceleration.y * 0.15, -0.003, 0.003), acceleration.z);
        player.connection.send(new ClientboundSetEntityMotionPacket(player));
    }

    private static void sendField(ServerLevel level, RiftCoreBlockEntity core, ServerPlayer player, boolean impact) {
        PacketDistributor.sendToPlayer(player, new RiftFieldPayload(level.dimension().location(), core.getBlockPos(),
                core.getFacing(), core.getRiftState(), RiftConfig.FIELD_RADIUS.get().floatValue(), impact));
    }
}
