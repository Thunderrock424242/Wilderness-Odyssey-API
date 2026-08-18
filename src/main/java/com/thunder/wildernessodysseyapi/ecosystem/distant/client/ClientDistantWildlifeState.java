package com.thunder.wildernessodysseyapi.ecosystem.distant.client;

import com.thunder.wildernessodysseyapi.core.ModConstants;
import com.thunder.wildernessodysseyapi.ecosystem.distant.network.DistantWildlifeSyncPayload;
import net.minecraft.client.multiplayer.ClientLevel;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.level.LevelEvent;

import java.util.List;
import java.util.Locale;

/** Client-owned immutable snapshot and renderer compatibility safety fuse. */
@EventBusSubscriber(modid = ModConstants.MOD_ID, value = Dist.CLIENT)
public final class ClientDistantWildlifeState {
    private static volatile DistantWildlifeSyncPayload snapshot;
    private static volatile String rendererDisabledReason;
    private static volatile long receivedAtNanos;
    private static volatile long packetSpacingNanos;
    private static volatile long packetsReceived;
    private static volatile RenderCounters renderCounters = RenderCounters.EMPTY;

    private ClientDistantWildlifeState() {
    }

    /** Atomically accepts a newer complete server snapshot. */
    public static void accept(DistantWildlifeSyncPayload payload) {
        DistantWildlifeSyncPayload previous = snapshot;
        if (previous != null
                && previous.dimension().equals(payload.dimension())
                && payload.sequence() <= previous.sequence()) {
            return;
        }
        long now = System.nanoTime();
        if (receivedAtNanos != 0L) {
            packetSpacingNanos = Math.max(1L, now - receivedAtNanos);
        }
        receivedAtNanos = now;
        packetsReceived++;
        snapshot = payload;
    }

    /** Returns the current dimension's usable snapshot, or null when disabled. */
    public static DistantWildlifeSyncPayload snapshot(ClientLevel level) {
        DistantWildlifeSyncPayload current = snapshot;
        if (rendererDisabledReason != null
                || current == null
                || !current.enabled()
                || !current.dimension().equals(level.dimension().location())) {
            return null;
        }
        return current;
    }

    /**
     * Permanently disables this renderer for the current client session.
     *
     * <p>This fail-closed boundary prevents an optional renderer integration
     * conflict from repeatedly crashing the level render loop.</p>
     */
    public static void disableRendererForSession(Throwable failure) {
        if (rendererDisabledReason != null) {
            return;
        }
        rendererDisabledReason = failure.getClass().getSimpleName()
                + (failure.getMessage() == null ? "" : ": " + failure.getMessage());
        ModConstants.LOGGER.warn(
                "Disabling distant wildlife rendering for this client session after a compatibility failure",
                failure
        );
    }

    /** Stores cheap frame counters for the cached Rendering debug page. */
    public static void recordRender(RenderCounters counters) {
        renderCounters = counters == null ? RenderCounters.EMPTY : counters;
    }

    /** Returns debug rows for visibility, avoided entities, LOD, and packet cadence. */
    public static List<String> debugLines() {
        DistantWildlifeSyncPayload current = snapshot;
        if (rendererDisabledReason != null) {
            return List.of(
                    "Renderer: DISABLED (compatibility safety fuse)",
                    "Reason: " + rendererDisabledReason
            );
        }
        if (current == null) {
            return List.of("State: awaiting server snapshot");
        }
        int represented = current.groups().stream()
                .mapToInt(DistantWildlifeSyncPayload.GroupSnapshot::populationEstimate)
                .sum();
        double frequency = packetSpacingNanos <= 0L ? 0.0 : 1_000_000_000.0 / packetSpacingNanos;
        double ageSeconds = receivedAtNanos <= 0L
                ? 0.0
                : Math.max(0L, System.nanoTime() - receivedAtNanos) / 1_000_000_000.0;
        return List.of(
                "State: " + (current.enabled() ? "ENABLED" : "DISABLED BY SERVER"),
                "Visible groups: " + renderCounters.visibleGroups + "/" + current.groups().size(),
                "Represented animals: " + represented,
                "Estimated active entities avoided: " + represented,
                "LOD transition/distant/fade: " + renderCounters.transitionGroups
                        + "/" + renderCounters.distantGroups + "/" + renderCounters.fadeGroups,
                "Frustum culled groups: " + renderCounters.frustumCulledGroups,
                "Distances real/transition/max: " + current.realEntityDistance()
                        + "/" + current.transitionBuffer() + "/" + current.distantWildlifeDistance(),
                "Configured update: " + current.updateInterval() + " ticks ("
                        + String.format(Locale.ROOT, "%.2f", 20.0 / current.updateInterval()) + " Hz)",
                "Observed packets: " + packetsReceived + " at "
                        + String.format(Locale.ROOT, "%.2f", frequency) + " Hz; age "
                        + String.format(Locale.ROOT, "%.1f", ageSeconds) + " s"
        );
    }

    /** Clears cross-world snapshots when the client tears down a level. */
    @SubscribeEvent
    public static void onLevelUnload(LevelEvent.Unload event) {
        if (event.getLevel() instanceof ClientLevel) {
            snapshot = null;
            receivedAtNanos = 0L;
            packetSpacingNanos = 0L;
            renderCounters = RenderCounters.EMPTY;
        }
    }

    /** One-frame renderer counters, kept separate from authoritative state. */
    public record RenderCounters(
            int visibleGroups,
            int transitionGroups,
            int distantGroups,
            int fadeGroups,
            int frustumCulledGroups
    ) {
        private static final RenderCounters EMPTY = new RenderCounters(0, 0, 0, 0, 0);
    }
}
