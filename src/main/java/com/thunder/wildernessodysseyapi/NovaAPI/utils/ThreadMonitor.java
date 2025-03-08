package com.thunder.wildernessodysseyapi.NovaAPI.utils;

import java.lang.management.ManagementFactory;
import java.lang.management.ThreadInfo;
import java.lang.management.ThreadMXBean;
import java.util.Map;

public class ThreadMonitor {

    private static final long CHECK_INTERVAL_MS = 10000; // 10 seconds
    private static Thread monitorThread;
    private static boolean running = false;

    public static void startMonitoring() {
        if (running) return; // Prevent multiple instances
        running = true;

        monitorThread = new Thread(() -> {
            while (running) {
                logAllThreads();
                checkForDeadlocks();
                try {
                    Thread.sleep(CHECK_INTERVAL_MS);
                } catch (InterruptedException e) {
                    System.err.println("Thread monitor interrupted.");
                }
            }
        }, "Nova-ThreadMonitor");

        monitorThread.setDaemon(true); // Allow JVM to exit without waiting
        monitorThread.start();
        System.out.println("✅ Nova API Thread Monitor started.");
    }

    public static void stopMonitoring() {
        running = false;
        if (monitorThread != null) {
            monitorThread.interrupt();
        }
    }

    private static void logAllThreads() {
        Map<Thread, StackTraceElement[]> threads = Thread.getAllStackTraces();
        System.out.println("📌 Active Threads: " + threads.size());

        for (Thread thread : threads.keySet()) {
            System.out.println("🧵 Thread Name: " + thread.getName() +
                    " | ID: " + thread.getId() +
                    " | State: " + thread.getState() +
                    " | Priority: " + thread.getPriority());
        }
    }

    private static void checkForDeadlocks() {
        ThreadMXBean threadBean = ManagementFactory.getThreadMXBean();
        long[] deadlockedThreadIds = threadBean.findDeadlockedThreads();

        if (deadlockedThreadIds != null) {
            System.err.println("⚠️ WARNING: Deadlocked threads detected!");
            ThreadInfo[] deadlockedThreads = threadBean.getThreadInfo(deadlockedThreadIds);
            for (ThreadInfo info : deadlockedThreads) {
                System.err.println("🚨 Deadlocked Thread: " + info.getThreadName() +
                        " | State: " + info.getThreadState() +
                        " | Lock Owner: " + info.getLockOwnerName());
            }
        } else {
            System.out.println("✅ No deadlocked threads detected.");
        }
    }
}