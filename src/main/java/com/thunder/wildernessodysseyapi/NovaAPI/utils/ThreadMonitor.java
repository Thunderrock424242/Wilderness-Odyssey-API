package com.thunder.wildernessodysseyapi.NovaAPI.utils;

import java.lang.management.ManagementFactory;
import java.lang.management.ThreadInfo;
import java.lang.management.ThreadMXBean;
import java.util.Map;

public class ThreadMonitor {

    public static void logAllThreads() {
        Map<Thread, StackTraceElement[]> threads = Thread.getAllStackTraces();
        System.out.println("Active Threads: " + threads.size());

        for (Thread thread : threads.keySet()) {
            System.out.println("Thread Name: " + thread.getName() +
                    " | ID: " + thread.getId() +
                    " | State: " + thread.getState() +
                    " | Priority: " + thread.getPriority());
        }

        checkForDeadlocks();
    }

    public static void checkForDeadlocks() {
        ThreadMXBean threadBean = ManagementFactory.getThreadMXBean();
        long[] deadlockedThreadIds = threadBean.findDeadlockedThreads();

        if (deadlockedThreadIds != null) {
            System.err.println("⚠️ WARNING: Deadlocked threads detected!");
            ThreadInfo[] deadlockedThreads = threadBean.getThreadInfo(deadlockedThreadIds);
            for (ThreadInfo info : deadlockedThreads) {
                System.err.println("Deadlocked Thread: " + info.getThreadName() +
                        " | State: " + info.getThreadState() +
                        " | Lock Owner: " + info.getLockOwnerName());
            }
        } else {
            System.out.println("✅ No deadlocked threads detected.");
        }
    }
}