package com.thunder.wildernessodysseyapi.dataengine;

import com.thunder.wildernessodysseyapi.dataengine.dirty.DirtyEntry;
import com.thunder.wildernessodysseyapi.dataengine.queue.UpdatePriority;
import com.thunder.wildernessodysseyapi.dataengine.scheduler.UpdateFrequency;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;

import java.util.Objects;
import java.util.function.IntSupplier;

/**
 * Declares one Wilderness subsystem's cadence and server-thread callbacks.
 *
 * <p>A system may be event-only, scheduled, dirty-driven, or combine scheduled
 * triggers with dirty final-state processing. The registration is immutable;
 * callbacks are always invoked by the logical server thread.</p>
 */
public final class DataSystemRegistration {
    private final ResourceLocation id;
    private final UpdateFrequency frequency;
    private final IntSupplier intervalTicks;
    private final UpdatePriority priority;
    private final ScheduledUpdateHandler scheduledHandler;
    private final DirtyUpdateHandler dirtyHandler;

    private DataSystemRegistration(Builder builder) {
        id = builder.id;
        frequency = builder.frequency;
        intervalTicks = builder.intervalTicks;
        priority = builder.priority;
        scheduledHandler = builder.scheduledHandler;
        dirtyHandler = builder.dirtyHandler;
        if (frequency == UpdateFrequency.EVENT_ONLY && scheduledHandler != null) {
            throw new IllegalArgumentException("Event-only systems cannot have a scheduled handler");
        }
    }

    public static Builder builder(ResourceLocation id) {
        return new Builder(id);
    }

    public ResourceLocation id() {
        return id;
    }

    public UpdateFrequency frequency() {
        return frequency;
    }

    public UpdatePriority priority() {
        return priority;
    }

    public ScheduledUpdateHandler scheduledHandler() {
        return scheduledHandler;
    }

    public DirtyUpdateHandler dirtyHandler() {
        return dirtyHandler;
    }

    /** Resolves and bounds the next interval on the server thread. */
    public int intervalTicks() {
        if (frequency == UpdateFrequency.EVENT_ONLY) {
            return 0;
        }
        return Math.max(1, intervalTicks.getAsInt());
    }

    /** Builder for an immutable system registration. */
    public static final class Builder {
        private final ResourceLocation id;
        private UpdateFrequency frequency = UpdateFrequency.EVENT_ONLY;
        private IntSupplier intervalTicks = () -> 1;
        private UpdatePriority priority = UpdatePriority.NORMAL;
        private ScheduledUpdateHandler scheduledHandler;
        private DirtyUpdateHandler dirtyHandler;

        private Builder(ResourceLocation id) {
            this.id = Objects.requireNonNull(id, "System id is required");
        }

        public Builder frequency(UpdateFrequency frequency) {
            this.frequency = Objects.requireNonNull(frequency, "Update frequency is required");
            this.intervalTicks = frequency::defaultIntervalTicks;
            return this;
        }

        /** Uses a live interval supplier so config reloads do not require re-registration. */
        public Builder intervalTicks(IntSupplier intervalTicks) {
            this.intervalTicks = Objects.requireNonNull(intervalTicks, "Interval supplier is required");
            return this;
        }

        public Builder priority(UpdatePriority priority) {
            this.priority = Objects.requireNonNull(priority, "Update priority is required");
            return this;
        }

        public Builder onScheduledUpdate(ScheduledUpdateHandler scheduledHandler) {
            this.scheduledHandler = Objects.requireNonNull(scheduledHandler, "Scheduled handler is required");
            return this;
        }

        public Builder onDirtyUpdate(DirtyUpdateHandler dirtyHandler) {
            this.dirtyHandler = Objects.requireNonNull(dirtyHandler, "Dirty handler is required");
            return this;
        }

        public DataSystemRegistration build() {
            return new DataSystemRegistration(this);
        }
    }

    /** SERVER THREAD ONLY. Handles a central scheduler invocation. */
    @FunctionalInterface
    public interface ScheduledUpdateHandler {
        void run(MinecraftServer server) throws Exception;
    }

    /** SERVER THREAD ONLY. Converts one final dirty entry into authoritative work or deltas. */
    @FunctionalInterface
    public interface DirtyUpdateHandler {
        void run(MinecraftServer server, DirtyEntry entry) throws Exception;
    }
}
