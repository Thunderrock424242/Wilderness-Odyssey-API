package com.thunder.wildernessodysseyapi.dataengine.scheduler;

import com.thunder.wildernessodysseyapi.dataengine.DataSystemRegistration;
import net.minecraft.resources.ResourceLocation;

import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.PriorityQueue;
import java.util.function.Consumer;

/**
 * SERVER THREAD ONLY. Central cadence scheduler for Wilderness-owned systems.
 *
 * <p>Only the next due registration is inspected. Systems are rescheduled from
 * the current tick rather than replaying every missed interval after lag, which
 * prevents catch-up storms.</p>
 */
public final class DataUpdateScheduler {
    private final PriorityQueue<ScheduledSystem> due = new PriorityQueue<>(
            Comparator.comparingLong(ScheduledSystem::nextTick)
    );
    private final Map<ResourceLocation, ScheduledSystem> registered = new HashMap<>();

    /** Registers a scheduled handler exactly once. Event-only systems are ignored. */
    public void register(DataSystemRegistration registration, long currentTick) {
        Objects.requireNonNull(registration, "System registration is required");
        if (registration.scheduledHandler() == null || registration.frequency() == UpdateFrequency.EVENT_ONLY) {
            return;
        }
        if (registered.containsKey(registration.id())) {
            throw new IllegalArgumentException("System is already scheduled: " + registration.id());
        }
        ScheduledSystem system = new ScheduledSystem(
                registration,
                saturatingAdd(currentTick, registration.intervalTicks())
        );
        registered.put(registration.id(), system);
        due.add(system);
    }

    /** Emits every currently due registration and advances its next deadline. */
    public int collectDue(long currentTick, Consumer<DataSystemRegistration> consumer) {
        Objects.requireNonNull(consumer, "Scheduled consumer is required");
        int count = 0;
        while (!due.isEmpty() && due.peek().nextTick <= currentTick) {
            ScheduledSystem system = due.poll();
            consumer.accept(system.registration);
            system.nextTick = saturatingAdd(currentTick, system.registration.intervalTicks());
            due.add(system);
            count++;
        }
        return count;
    }

    public int size() {
        return registered.size();
    }

    public void clear() {
        due.clear();
        registered.clear();
    }

    private static long saturatingAdd(long value, int increment) {
        return value > Long.MAX_VALUE - increment ? Long.MAX_VALUE : value + increment;
    }

    private static final class ScheduledSystem {
        private final DataSystemRegistration registration;
        private long nextTick;

        private ScheduledSystem(DataSystemRegistration registration, long nextTick) {
            this.registration = registration;
            this.nextTick = nextTick;
        }

        long nextTick() {
            return nextTick;
        }
    }
}
