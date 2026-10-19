package com.thunder.wildernessodysseyapi.opc.ram;

import com.thunder.wildernessodysseyapi.opc.core.JvmMetrics;
import java.util.concurrent.atomic.AtomicBoolean;

public final class RamAdvisorService {
    private static final RamAdvisorService INSTANCE = new RamAdvisorService();
    private static final long SAMPLE_INTERVAL_NANOS = 1_000_000_000L;

    private final RamAdvisorEngine engine = new RamAdvisorEngine();
    private final AtomicBoolean running = new AtomicBoolean(false);
    private GcMonitor gcMonitor;
    private long lastSampleNanos;

    private RamAdvisorService() {}

    public static RamAdvisorService get() {
        return INSTANCE;
    }

    public synchronized void start() {
        if (!running.compareAndSet(false, true)) return;
        engine.reset();
        gcMonitor = new GcMonitor(engine::addGcSample);
        gcMonitor.start();
        lastSampleNanos = 0L;
        sampleNow();
    }

    public synchronized void stop() {
        if (!running.compareAndSet(true, false)) return;
        if (gcMonitor != null) {
            gcMonitor.close();
            gcMonitor = null;
        }
    }

    public void tick() {
        if (!running.get()) return;
        long now = System.nanoTime();
        if (lastSampleNanos != 0L && now - lastSampleNanos < SAMPLE_INTERVAL_NANOS) return;
        lastSampleNanos = now;
        sampleNow();
    }

    public synchronized void reset() {
        engine.reset();
        lastSampleNanos = 0L;
        if (running.get()) sampleNow();
    }

    public RamRecommendation recommendation() {
        return engine.recommend();
    }

    public int sampleCount() {
        return engine.sampleCount();
    }

    public int gcSampleCount() {
        return engine.gcSampleCount();
    }

    private void sampleNow() {
        var raw = JvmMetrics.shared().sample(System.nanoTime());
        engine.addSample(new RamSample(
                raw.timestampMillis(),
                Math.max(0L, raw.heapUsedBytes()),
                Math.max(0L, raw.heapCommittedBytes()),
                Math.max(0L, raw.heapMaximumBytes()),
                Math.max(0L, raw.nonHeapUsedBytes()),
                Math.max(0L, raw.systemTotalBytes()),
                Math.max(0L, raw.systemFreeBytes())
        ));
    }
}
