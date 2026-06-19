package com.thunder.wildernessodysseyapi.gpuprofiler.client;

import net.minecraft.resources.ResourceLocation;

import java.util.ArrayDeque;
import java.util.Deque;

/** Carries a resource identity through synchronous texture allocation calls. */
public final class GpuProfilerContext {

    private static final ThreadLocal<Deque<ResourceLocation>> RESOURCES = ThreadLocal.withInitial(ArrayDeque::new);

    private GpuProfilerContext() {
    }

    public static Scope withResource(ResourceLocation resource) {
        if (!GpuProfiler.isActive() || resource == null) {
            return Scope.NOOP;
        }
        RESOURCES.get().push(resource);
        return new Scope(true);
    }

    static ResourceLocation currentResource() {
        return RESOURCES.get().peek();
    }

    public static final class Scope implements AutoCloseable {
        private static final Scope NOOP = new Scope(false);
        private final boolean pushed;

        private Scope(boolean pushed) {
            this.pushed = pushed;
        }

        @Override
        public void close() {
            if (!this.pushed) {
                return;
            }
            Deque<ResourceLocation> resources = RESOURCES.get();
            if (!resources.isEmpty()) {
                resources.pop();
            }
            if (resources.isEmpty()) {
                RESOURCES.remove();
            }
        }
    }
}
