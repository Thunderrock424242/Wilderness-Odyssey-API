package com.thunder.wildernessodysseyapi.anomaly;

import com.thunder.wildernessodysseyapi.anomaly.network.AnomalyAtmospherePayload;
import com.thunder.wildernessodysseyapi.core.ModConstants;
import com.thunder.wildernessodysseyapi.temporalrift.TemporalRiftSavedData;
import com.thunder.wildernessodysseyapi.worldgen.anomaly.AnomalyZone;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;

/**
 * Sends only local biome/rift conclusions at most once per second.
 * The biome source and temporal-rift saved data retain authority; this class
 * never changes terrain, weather, rift state, or chunks and never sends a seed.
 */
@EventBusSubscriber(modid = ModConstants.MOD_ID)
public final class AnomalyAtmosphereSync {
    private record Sent(AnomalyAtmospherePayload payload, long tick) { }
    private static final Map<MinecraftServer, Map<UUID, Sent>> LAST_SENT = new WeakHashMap<>();
    private static final Map<MinecraftServer, AnomalyPreviewOverrides> PREVIEWS = new WeakHashMap<>();

    private AnomalyAtmosphereSync() { }

    @SubscribeEvent
    public static void onTick(ServerTickEvent.Post event) {
        MinecraftServer server = event.getServer();
        if (server.getTickCount() % 20 != 0) return;
        long tick = server.overworld().getGameTime();
        Map<UUID, Sent> sent = LAST_SENT.computeIfAbsent(server, ignored -> new HashMap<>());
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            AnomalyAtmospherePayload next = snapshot(player);
            Sent previous = sent.get(player.getUUID());
            if (previous == null || AnomalyAtmospherePolicy.shouldSend(previous.tick(), tick,
                    !next.equals(previous.payload()))) {
                PacketDistributor.sendToPlayer(player, next);
                sent.put(player.getUUID(), new Sent(next, tick));
            }
        }
    }

    /** Samples only nine already-loaded columns; missing edge samples have zero influence. */
    public static AnomalyAtmospherePayload snapshot(ServerPlayer player) {
        ServerLevel level = player.serverLevel();
        BlockPos position = player.blockPosition();
        double[] samples = new double[9];
        int zone = -1;
        int index = 0;
        for (int z = -1; z <= 1; z++) {
            for (int x = -1; x <= 1; x++) {
                BlockPos sample = position.offset(x * 12, 0, z * 12);
                if (level.getChunkSource().getChunkNow(sample.getX() >> 4, sample.getZ() >> 4) != null) {
                    var biomeZone = level.getBiome(sample).unwrapKey().flatMap(AnomalyZone::fromBiome);
                    samples[index] = biomeZone.map(AnomalyZone::intensity).orElse(0.0);
                    if (x == 0 && z == 0) zone = biomeZone.map(Enum::ordinal).orElse(-1);
                }
                index++;
            }
        }
        double intensity = AnomalyAtmospherePolicy.blend(samples);
        var previews = PREVIEWS.get(player.getServer());
        if (previews != null) {
            var preview = previews.get(player.getUUID(), level.dimension().location().toString(),
                    player.getServer().overworld().getGameTime());
            if (preview.isPresent()) {
                zone = preview.get().zone();
                intensity = preview.get().intensity();
            }
        }
        double riftIntensity = 0;
        if (level.dimension().equals(Level.OVERWORLD)) {
            TemporalRiftSavedData rift = TemporalRiftSavedData.get(player.getServer());
            if (rift.isRiftOpen() && rift.getRiftPosition() != null) {
                riftIntensity = AnomalyAtmospherePolicy.riftInfluence(
                        Math.sqrt(position.distSqr(rift.getRiftPosition())));
            }
        }
        return new AnomalyAtmospherePayload(level.dimension().location(), zone,
                (int) Math.round(intensity * 100), (int) Math.round(riftIntensity * 100));
    }

    public static void preview(ServerPlayer player, AnomalyZone zone, double intensity) {
        long tick = player.getServer().overworld().getGameTime();
        PREVIEWS.computeIfAbsent(player.getServer(), ignored -> new AnomalyPreviewOverrides())
                .set(player.getUUID(), player.level().dimension().location().toString(),
                        zone.ordinal(), intensity, tick, tick + 1_200);
        LAST_SENT.computeIfAbsent(player.getServer(), ignored -> new HashMap<>()).remove(player.getUUID());
        PacketDistributor.sendToPlayer(player, snapshot(player));
    }

    public static void resetPreview(ServerPlayer player) {
        clearPlayer(player);
        PacketDistributor.sendToPlayer(player, snapshot(player));
    }

    private static void clearPlayer(ServerPlayer player) {
        Map<UUID, Sent> sent = LAST_SENT.get(player.getServer());
        if (sent != null) sent.remove(player.getUUID());
        AnomalyPreviewOverrides previews = PREVIEWS.get(player.getServer());
        if (previews != null) previews.clear(player.getUUID());
    }

    @SubscribeEvent
    public static void onTravel(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) clearPlayer(player);
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) clearPlayer(player);
    }

    @SubscribeEvent
    public static void onRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) clearPlayer(player);
    }

    @SubscribeEvent
    public static void onStop(ServerStoppedEvent event) {
        LAST_SENT.remove(event.getServer());
        PREVIEWS.remove(event.getServer());
    }
}
