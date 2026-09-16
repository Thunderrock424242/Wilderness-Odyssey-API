package com.thunder.wildernessodysseyapi.temporalrift.echo;

import com.thunder.wildernessodysseyapi.temporalrift.config.TemporalRiftConfig;
import com.thunder.wildernessodysseyapi.temporalrift.registry.TemporalRiftDimensions;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;

/** Scheduled player-local synchronization; the existing build-echo manager still owns all queued edits. */
public final class EchoSyncManager {
    private static final Map<MinecraftServer, Map<UUID, EchoStabilityPayload>> LAST_SENT = new WeakHashMap<>();

    private EchoSyncManager() { }

    /** At most one changed summary per player per second, plus a ten-second refresh. */
    public static void tick(MinecraftServer server) {
        if (server.overworld().getGameTime() % 20 != 0) return;
        ServerLevel level = server.getLevel(TemporalRiftDimensions.THE_ECHO_KEY);
        if (level == null) return;
        Map<UUID, EchoStabilityPayload> sent = LAST_SENT.computeIfAbsent(server, ignored -> new HashMap<>());
        for (ServerPlayer player : level.players()) {
            EchoRegionState state = EchoStabilityManager.getStabilityAt(level, player.blockPosition());
            EchoDiscoveryManager.tick(player);
            if (state.nearestFracture() != null && state.fractureInfluence() > 0.5 && !state.overridden()) {
                EchoDiscoveryManager.observeFracture(player);
            }
            observeRemnants(player);
            EchoStabilityPayload next = new EchoStabilityPayload(level.dimension().location(), level.getGameTime(),
                    state.regionId(), (int) Math.round(state.intensity() * 100),
                    (int) Math.round(EchoStabilityManager.riftfallIntensity(level, player.blockPosition()) * 100),
                    TemporalRiftConfig.ENABLE_ECHO_ATMOSPHERIC_DISTORTION.get(),
                    TemporalRiftConfig.ENABLE_ECHO_TIME_ANOMALIES.get());
            EchoStabilityPayload previous = sent.get(player.getUUID());
            if (previous == null || next.gameTime() - previous.gameTime() >= 200 || next.gameTime() < previous.gameTime()
                    || next.regionId() != previous.regionId() || next.intensity() != previous.intensity()
                    || next.riftfallIntensity() != previous.riftfallIntensity()
                    || next.atmosphere() != previous.atmosphere() || next.timeAnomalies() != previous.timeAnomalies()) {
                PacketDistributor.sendToPlayer(player, next);
                sent.put(player.getUUID(), next);
            }
        }
    }

    /** Announces an actual applied material echo with a tiny local effect; never invents a rift or weather event. */
    public static void onRealityEcho(ServerLevel level, BlockPos position) {
        if (TemporalRiftConfig.ENABLE_ECHO_ATMOSPHERIC_DISTORTION.get()) {
            level.sendParticles(ParticleTypes.REVERSE_PORTAL, position.getX() + 0.5, position.getY() + 0.5,
                    position.getZ() + 0.5, 3, 0.3, 0.3, 0.3, 0.01);
        }
    }

    private static void observeRemnants(ServerPlayer player) {
        if (EchoDiscoveryManager.contextTags(player).contains(EchoDiscoveryStage.MATERIAL_SYNCHRONIZATION.contextTag())) return;
        var level = player.serverLevel();
        var chunk = level.getChunkSource().getChunkNow(player.getBlockX() >> 4, player.getBlockZ() >> 4);
        if (chunk == null || !chunk.hasData(com.thunder.wildernessodysseyapi.core.ModAttachments.ECHO_CHUNK)) return;
        for (var marker : chunk.getData(com.thunder.wildernessodysseyapi.core.ModAttachments.ECHO_CHUNK).markers().entrySet()) {
            BlockPos position = marker.getKey();
            // Only the player's already-loaded chunk is inspected; old markers outside it are ignored.
            if (position.getX() >> 4 != chunk.getPos().x || position.getZ() >> 4 != chunk.getPos().z
                    || position.distSqr(player.blockPosition()) > 16 * 16) continue;
            if (!net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(chunk.getBlockState(position).getBlock()).equals(marker.getValue())) continue;
            var hit = level.clip(new net.minecraft.world.level.ClipContext(player.getEyePosition(),
                    net.minecraft.world.phys.Vec3.atCenterOf(position), net.minecraft.world.level.ClipContext.Block.COLLIDER,
                    net.minecraft.world.level.ClipContext.Fluid.NONE, player));
            if (hit.getType() == net.minecraft.world.phys.HitResult.Type.BLOCK && hit.getBlockPos().equals(position)) {
                EchoDiscoveryManager.observeRealityEcho(player);
                break;
            }
        }
    }

    /** Clears last-sent state at logout or dimension change so re-entry receives a fresh summary. */
    public static void clearPlayer(ServerPlayer player) {
        Map<UUID, EchoStabilityPayload> sent = LAST_SENT.get(player.getServer());
        if (sent != null) sent.remove(player.getUUID());
    }

    /** Releases references and caches when the owning server closes. */
    public static void clear(MinecraftServer server) {
        LAST_SENT.remove(server);
        EchoStabilityManager.clear(server);
    }
}
