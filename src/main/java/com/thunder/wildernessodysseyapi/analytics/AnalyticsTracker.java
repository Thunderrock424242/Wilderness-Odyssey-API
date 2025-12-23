package com.thunder.wildernessodysseyapi.analytics;

import com.sun.management.OperatingSystemMXBean;
import com.thunder.wildernessodysseyapi.AI.AI_perf.PerformanceAdvisor;
import com.thunder.wildernessodysseyapi.Core.ModConstants;
import com.thunder.wildernessodysseyapi.Core.WildernessOdysseyAPIMainModClass;
import com.thunder.wildernessodysseyapi.MemUtils.MemoryUtils;
import com.thunder.wildernessodysseyapi.globalchat.GlobalChatManager;
import net.minecraft.server.MinecraftServer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.lang.management.ManagementFactory;
import java.nio.file.Path;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.concurrent.TimeUnit;

import static com.thunder.wildernessodysseyapi.Core.ModConstants.MOD_ID;

/**
 * Samples resource usage and streams it to the global chat relay for external consumption.
 */
@EventBusSubscriber(modid = MOD_ID)
public class AnalyticsTracker {

    private static final int SAMPLE_INTERVAL_TICKS = 600;
    private static final double OVERLOAD_CPU_THRESHOLD = 0.90d;

    private static final OperatingSystemMXBean OS_BEAN =
            ManagementFactory.getPlatformMXBean(OperatingSystemMXBean.class);

    private static final GlobalChatManager CHAT = GlobalChatManager.getInstance();

    private static AnalyticsAccessSettings accessSettings = new AnalyticsAccessSettings();

    private static MinecraftServer server;
    private static int tickCounter = 0;
    private static long lastTickTimeNanos = 0L;
    private static long worstTickTimeNanos = 0L;
    private static AnalyticsSnapshot lastSnapshot;
    private static AnalyticsSnapshot previousSnapshot;

    public static void initialize(MinecraftServer minecraftServer, Path configDir) {
        server = minecraftServer;
        Path file = configDir.resolve("wildernessodysseyapi/analytics-access.json");
        accessSettings = AnalyticsAccessSettings.load(file);
        accessSettings.save(file);
    }

    public static void shutdown() {
        server = null;
        lastSnapshot = null;
        previousSnapshot = null;
        tickCounter = 0;
        lastTickTimeNanos = 0L;
        worstTickTimeNanos = 0L;
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        long now = System.nanoTime();
        if (lastTickTimeNanos != 0L) {
            long duration = now - lastTickTimeNanos;
            if (duration > worstTickTimeNanos) {
                worstTickTimeNanos = duration;
            }
        }
        lastTickTimeNanos = now;

        if (!event.hasTime()) {
            return;
        }

        if (++tickCounter >= SAMPLE_INTERVAL_TICKS) {
            tickCounter = 0;
            captureAndSend(event.getServer());
        }
    }

    public static Optional<AnalyticsSnapshot> lastSnapshot() {
        return Optional.ofNullable(lastSnapshot);
    }

    public static boolean isAllowed(UUID uuid) {
        return accessSettings.isAllowed(uuid);
    }

    private static void captureAndSend(MinecraftServer server) {
        AnalyticsSnapshot snapshot = new AnalyticsSnapshot();
        snapshot.playerCount = server.getPlayerCount();
        snapshot.maxPlayers = server.getMaxPlayers();
        snapshot.usedMemoryMb = MemoryUtils.getUsedMemoryMB();
        snapshot.totalMemoryMb = MemoryUtils.getTotalMemoryMB();
        snapshot.peakMemoryMb = MemoryUtils.getPeakUsedMemoryMB();
        snapshot.recommendedMemoryMb = MemoryUtils.calculateRecommendedRAM(snapshot.usedMemoryMb,
                WildernessOdysseyAPIMainModClass.dynamicModCount);
        snapshot.worstTickMillis = TimeUnit.NANOSECONDS.toMillis(worstTickTimeNanos);
        snapshot.cpuLoad = readCpuLoad();
        snapshot.overloaded = snapshot.worstTickMillis > PerformanceAdvisor.DEFAULT_TICK_BUDGET_MS
                || snapshot.cpuLoad >= OVERLOAD_CPU_THRESHOLD;
        snapshot.overloadedReason = snapshot.overloaded
                ? "Tick or CPU budget exceeded"
                : "Stable";
        snapshot.players = server.getPlayerList().getPlayers().stream()
                .map(player -> {
                    AnalyticsSnapshot.PlayerStats stats = new AnalyticsSnapshot.PlayerStats();
                    stats.uuid = player.getUUID().toString();
                    stats.name = player.getGameProfile().getName();
                    stats.pingMillis = player.connection.latency();
                    stats.dimension = player.level().dimension().location().toString();
                    return stats;
                })
                .collect(Collectors.toList());

        worstTickTimeNanos = 0L;
        previousSnapshot = lastSnapshot;
        lastSnapshot = snapshot;
        AnalyticsSyncView syncView = AnalyticsSyncView.fromSnapshots(snapshot, previousSnapshot);
        CHAT.sendAnalyticsSnapshot(snapshot, syncView);
        ModConstants.LOGGER.debug("[Analytics] Sent snapshot: players={}, cpuLoad={} tick={}ms", snapshot.playerCount,
                snapshot.cpuLoad, snapshot.worstTickMillis);
    }

    private static double readCpuLoad() {
        double processLoad = OS_BEAN.getProcessCpuLoad();
        if (processLoad >= 0) {
            return processLoad;
        }
        double systemLoad = OS_BEAN.getSystemCpuLoad();
        return Math.max(0, systemLoad);
    }
}
