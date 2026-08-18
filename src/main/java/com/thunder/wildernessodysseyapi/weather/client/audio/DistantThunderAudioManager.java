package com.thunder.wildernessodysseyapi.weather.client.audio;

import com.thunder.wildernessodysseyapi.item.ModSoundEvents;
import com.thunder.wildernessodysseyapi.weather.api.WeatherSample;
import com.thunder.wildernessodysseyapi.weather.client.ClientWeatherCoordinator;
import com.thunder.wildernessodysseyapi.weather.config.WeatherRenderingConfig;
import com.thunder.wildernessodysseyapi.weather.networking.DistantThunderSystemSyncPayload;
import com.thunder.wildernessodysseyapi.weather.system.WeatherSystemStage;
import com.thunder.wildernessodysseyapi.weather.system.WeatherSystemType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Owns low-frequency, per-client scheduling and directional distant-thunder playback.
 *
 * <p>The server sends only authoritative storm summaries. This manager evaluates
 * them every two seconds, caches one relevant storm, and plays audio locally so
 * several players hear independent results without sound packets or helper entities.</p>
 */
public final class DistantThunderAudioManager {

    private static final int EVALUATION_INTERVAL_TICKS = 40;
    private static final double VIRTUAL_SOUND_DISTANCE = 10.0;
    private static final Object UPDATE_LOCK = new Object();
    private static final Map<net.minecraft.resources.ResourceLocation, Long> SEQUENCE_WATERMARKS =
            new HashMap<>();
    private static final RandomSource RANDOM = RandomSource.create();

    private static volatile SyncedState syncedState;
    private static DistantThunderModel.Selection selected;
    private static int evaluationTimer;
    private static int nextThunderTimer = -1;
    private static ThunderVariant previousVariant;
    private static ThunderVariant lastPlayedVariant;
    private static boolean localTakeover;

    private DistantThunderAudioManager() {
    }

    /** Validates and accepts one newest server-authored storm summary. */
    public static boolean accept(DistantThunderSystemSyncPayload payload) {
        if (payload == null || payload.dataVersion() != DistantThunderSystemSyncPayload.DATA_VERSION) {
            return false;
        }
        Minecraft minecraft = Minecraft.getInstance();
        ClientLevel level = minecraft.level;
        if (level == null || !level.dimension().location().equals(payload.dimension())) {
            return false;
        }
        synchronized (UPDATE_LOCK) {
            long watermark = SEQUENCE_WATERMARKS.getOrDefault(payload.dimension(), -1L);
            if (payload.sequence() <= watermark) {
                return false;
            }
            SEQUENCE_WATERMARKS.put(payload.dimension(), payload.sequence());
            syncedState = payload.enabled()
                    ? new SyncedState(payload.dimension(), payload.sequence(), payload.storms())
                    : null;
            if (!payload.enabled()) {
                resetPlayback();
            }
            evaluationTimer = 0;
            return true;
        }
    }

    /** Advances cached selection and randomized playback from the client tick event. */
    public static void tick(Minecraft minecraft) {
        ClientLevel level = minecraft == null ? null : minecraft.level;
        WeatherRenderingConfig.Settings settings = WeatherRenderingConfig.settings();
        if (level == null
                || minecraft.player == null
                || minecraft.isPaused()
                || !settings.distantThunderEnabled()
                || !ClientWeatherCoordinator.controls(level)) {
            resetPlayback();
            return;
        }

        SyncedState state = syncedState;
        if (state == null || !state.dimension().equals(level.dimension().location())) {
            resetPlayback();
            return;
        }

        if (nextThunderTimer > 0) {
            nextThunderTimer--;
        }
        if (evaluationTimer-- <= 0) {
            evaluate(minecraft, level, state, settings);
            evaluationTimer = EVALUATION_INTERVAL_TICKS;
        }
        if (selected != null && nextThunderTimer == 0) {
            play(minecraft, level, selected);
            nextThunderTimer = DistantThunderModel.nextIntervalTicks(
                    selected,
                    settings,
                    RANDOM.nextDouble()
            );
        }
    }

    /** Returns immutable state formatted only when the F3 weather page requests it. */
    public static Diagnostics diagnostics() {
        SyncedState state = syncedState;
        DistantThunderModel.Selection current = selected;
        return current == null
                ? new Diagnostics(
                        false,
                        state == null ? 0 : state.storms().size(),
                        -1L,
                        null,
                        null,
                        null,
                        null,
                        Double.POSITIVE_INFINITY,
                        0.0,
                        Math.max(-1, nextThunderTimer),
                        0.0,
                        lastPlayedVariant == null ? "none" : lastPlayedVariant.debugName,
                        localTakeover
                )
                : new Diagnostics(
                        true,
                        state == null ? 0 : state.storms().size(),
                        current.storm().id(),
                        current.storm().type(),
                        current.storm().stage(),
                        current.classification(),
                        current.movement(),
                        current.distanceBlocks(),
                        current.storm().intensity(),
                        Math.max(-1, nextThunderTimer),
                        current.volume(),
                        lastPlayedVariant == null ? "none" : lastPlayedVariant.debugName,
                        localTakeover
                );
    }

    /** Clears state tied to an unloading client dimension. */
    public static void clearLevel(ClientLevel level) {
        if (level == null) {
            return;
        }
        synchronized (UPDATE_LOCK) {
            SyncedState state = syncedState;
            if (state != null && state.dimension().equals(level.dimension().location())) {
                syncedState = null;
                resetPlayback();
            }
        }
    }

    /** Clears connection-scoped sequences and all playback state on login/logout. */
    public static void clearAll() {
        synchronized (UPDATE_LOCK) {
            syncedState = null;
            SEQUENCE_WATERMARKS.clear();
            resetPlayback();
        }
    }

    private static void evaluate(
            Minecraft minecraft,
            ClientLevel level,
            SyncedState state,
            WeatherRenderingConfig.Settings settings
    ) {
        Vec3 listener = minecraft.gameRenderer.getMainCamera().getPosition();
        WeatherSample localWeather = ClientWeatherCoordinator.sampleAt(level, listener);
        localTakeover = localWeather.lightningEligible();
        long previousId = selected == null ? -1L : selected.storm().id();
        DistantThunderModel.Selection next = DistantThunderModel.select(
                state.storms(),
                listener.x,
                listener.z,
                localWeather,
                settings,
                previousId
        );
        boolean changed = next == null || selected == null || next.storm().id() != selected.storm().id();
        selected = next;
        if (selected == null) {
            nextThunderTimer = -1;
        } else if (changed || nextThunderTimer < 0) {
            nextThunderTimer = DistantThunderModel.nextIntervalTicks(
                    selected,
                    settings,
                    RANDOM.nextDouble()
            );
        } else {
            // An already-scheduled far rumble must not retain an obsolete long
            // delay after the same storm moves substantially closer.
            nextThunderTimer = Math.min(
                    nextThunderTimer,
                    DistantThunderModel.nextIntervalTicks(selected, settings, 1.0)
            );
        }
    }

    private static void play(
            Minecraft minecraft,
            ClientLevel level,
            DistantThunderModel.Selection selection
    ) {
        Vec3 listener = minecraft.gameRenderer.getMainCamera().getPosition();
        double deltaX = selection.storm().centerX() - listener.x;
        double deltaZ = selection.storm().centerZ() - listener.z;
        double length = Math.hypot(deltaX, deltaZ);
        if (length < 1.0E-4) {
            deltaX = 1.0;
            deltaZ = 0.0;
            length = 1.0;
        }
        double soundX = listener.x + deltaX / length * VIRTUAL_SOUND_DISTANCE;
        double soundZ = listener.z + deltaZ / length * VIRTUAL_SOUND_DISTANCE;
        ThunderVariant variant = nextVariant(previousVariant);
        previousVariant = variant;
        lastPlayedVariant = variant;
        float volume = (float) Math.max(0.0, Math.min(1.5, selection.volume() * variant.volumeScale));
        level.playLocalSound(
                soundX,
                listener.y + 2.0,
                soundZ,
                variant.sound(),
                SoundSource.WEATHER,
                volume,
                variant.pitch,
                false
        );
    }

    private static ThunderVariant nextVariant(ThunderVariant excluded) {
        ThunderVariant[] variants = ThunderVariant.values();
        if (excluded == null) {
            return variants[RANDOM.nextInt(variants.length)];
        }
        int index = RANDOM.nextInt(variants.length - 1);
        if (index >= excluded.ordinal()) {
            index++;
        }
        return variants[index];
    }

    private static void resetPlayback() {
        selected = null;
        evaluationTimer = 0;
        nextThunderTimer = -1;
        previousVariant = null;
        localTakeover = false;
    }

    private record SyncedState(
            net.minecraft.resources.ResourceLocation dimension,
            long sequence,
            List<DistantThunderSystemSyncPayload.StormSnapshot> storms
    ) {
        private SyncedState {
            storms = List.copyOf(storms);
        }
    }

    private enum ThunderVariant {
        LOW_RUMBLE("low rumble", 0.62F, 0.82F),
        ROLLING_THUNDER("rolling thunder", 0.78F, 0.90F),
        DEEP_DISTANT_CRACK("deep distant crack", 0.90F, 1.0F),
        LONG_RUMBLE("long rumble", 0.55F, 0.85F);

        private final String debugName;
        private final float pitch;
        private final float volumeScale;

        ThunderVariant(String debugName, float pitch, float volumeScale) {
            this.debugName = debugName;
            this.pitch = pitch;
            this.volumeScale = volumeScale;
        }

        private SoundEvent sound() {
            return switch (this) {
                case LOW_RUMBLE -> ModSoundEvents.DISTANT_THUNDER_LOW_RUMBLE.get();
                case ROLLING_THUNDER -> ModSoundEvents.DISTANT_THUNDER_ROLLING.get();
                case DEEP_DISTANT_CRACK -> ModSoundEvents.DISTANT_THUNDER_DEEP_CRACK.get();
                case LONG_RUMBLE -> ModSoundEvents.DISTANT_THUNDER_LONG_RUMBLE.get();
            };
        }
    }

    /** Compact F3-facing state for the currently selected storm and timer. */
    public record Diagnostics(
            boolean selected,
            int synchronizedStorms,
            long stormId,
            WeatherSystemType type,
            WeatherSystemStage stage,
            DistantThunderModel.ThunderstormClassification classification,
            DistantThunderModel.Movement movement,
            double distanceBlocks,
            double stormIntensity,
            int nextThunderTicks,
            double calculatedVolume,
            String lastVariant,
            boolean localTakeover
    ) {
    }
}
