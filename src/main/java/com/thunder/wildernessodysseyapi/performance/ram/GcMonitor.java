package com.thunder.wildernessodysseyapi.performance.ram;

import com.sun.management.GarbageCollectionNotificationInfo;
import com.sun.management.GcInfo;

import javax.management.Notification;
import javax.management.NotificationEmitter;
import javax.management.NotificationListener;
import javax.management.openmbean.CompositeData;
import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryUsage;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

public final class GcMonitor implements AutoCloseable {
    private final Consumer<GcSample> sink;
    private final List<Registration> registrations = new CopyOnWriteArrayList<>();

    public GcMonitor(Consumer<GcSample> sink) {
        this.sink = sink;
    }

    public void start() {
        if (!registrations.isEmpty()) return;

        for (GarbageCollectorMXBean bean : ManagementFactory.getGarbageCollectorMXBeans()) {
            if (!(bean instanceof NotificationEmitter emitter)) continue;

            NotificationListener listener = this::onNotification;
            try {
                emitter.addNotificationListener(listener, null, null);
                registrations.add(new Registration(emitter, listener));
            } catch (Exception ignored) {
                // GC notifications are optional. The advisor still works with heap samples alone.
            }
        }
    }

    private void onNotification(Notification notification, Object handback) {
        if (!GarbageCollectionNotificationInfo.GARBAGE_COLLECTION_NOTIFICATION.equals(notification.getType())) return;
        if (!(notification.getUserData() instanceof CompositeData data)) return;

        GarbageCollectionNotificationInfo info = GarbageCollectionNotificationInfo.from(data);
        GcInfo gcInfo = info.getGcInfo();

        long before = sumHeapUsage(gcInfo.getMemoryUsageBeforeGc());
        long after = sumHeapUsage(gcInfo.getMemoryUsageAfterGc());

        sink.accept(new GcSample(
                System.currentTimeMillis(),
                info.getGcName(),
                info.getGcAction(),
                info.getGcCause(),
                gcInfo.getDuration(),
                before,
                after
        ));
    }

    private static long sumHeapUsage(Map<String, MemoryUsage> usageMap) {
        List<String> heapPools = new ArrayList<>();
        ManagementFactory.getMemoryPoolMXBeans().forEach(pool -> {
            if (pool.getType() == java.lang.management.MemoryType.HEAP) heapPools.add(pool.getName());
        });

        long total = 0L;
        for (String poolName : heapPools) {
            MemoryUsage usage = usageMap.get(poolName);
            if (usage != null) total += Math.max(0L, usage.getUsed());
        }
        return total;
    }

    @Override
    public void close() {
        for (Registration registration : registrations) {
            try {
                registration.emitter.removeNotificationListener(registration.listener);
            } catch (Exception ignored) {
            }
        }
        registrations.clear();
    }

    private record Registration(NotificationEmitter emitter, NotificationListener listener) {}
}
