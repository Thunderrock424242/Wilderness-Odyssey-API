package com.thunder.wildernessodysseyapi.vegetation.simulation;

import com.thunder.wildernessodysseyapi.vegetation.api.PlantDisturbance;
import com.thunder.wildernessodysseyapi.vegetation.api.VegetationDisturbanceSample;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.WeakHashMap;

/** Server-thread, constant-size ledger of recent regional plant disturbances. */
public final class VegetationDisturbanceLedger {

    private static final int MAXIMUM_EVENTS_PER_LEVEL = 256;
    private static final Map<ServerLevel, Deque<PlantDisturbance>> LEVELS = new WeakHashMap<>();

    private VegetationDisturbanceLedger() {
    }

    /** Records one validated request, evicting the oldest event at the hard cap. */
    public static void record(ServerLevel level, PlantDisturbance disturbance) {
        if (level == null || disturbance == null || disturbance.intensity() <= 0.0) {
            return;
        }
        Deque<PlantDisturbance> events = LEVELS.computeIfAbsent(level, ignored -> new ArrayDeque<>());
        prune(events, level.getGameTime());
        while (events.size() >= MAXIMUM_EVENTS_PER_LEVEL) {
            events.removeFirst();
        }
        events.addLast(disturbance);
    }

    /** Returns the strongest active request without loading or scanning chunks. */
    public static VegetationDisturbanceSample sample(ServerLevel level, BlockPos position) {
        if (level == null || position == null) {
            return VegetationDisturbanceSample.NONE;
        }
        Deque<PlantDisturbance> events = LEVELS.get(level);
        if (events == null || events.isEmpty()) {
            return VegetationDisturbanceSample.NONE;
        }
        long gameTime = level.getGameTime();
        prune(events, gameTime);
        PlantDisturbance strongest = null;
        double strongestIntensity = 0.0;
        for (PlantDisturbance event : events) {
            double local = event.intensityAt(position, gameTime);
            if (local > strongestIntensity) {
                strongest = event;
                strongestIntensity = local;
            }
        }
        return strongest == null
                ? VegetationDisturbanceSample.NONE
                : new VegetationDisturbanceSample(
                        strongest.type(),
                        strongestIntensity,
                        strongest.allowBlockDamage(),
                        strongest.expiresAt()
                );
    }

    /** Releases an unloading dimension's ephemeral event history. */
    public static void clear(ServerLevel level) {
        LEVELS.remove(level);
    }

    /** Releases all event history after server shutdown. */
    public static void clearAll() {
        LEVELS.clear();
    }

    private static void prune(Deque<PlantDisturbance> events, long gameTime) {
        events.removeIf(event -> event.expiresAt() <= gameTime);
    }
}
