package com.thunder.wildernessodysseyapi.NovaAPI;

import com.thunder.wildernessodysseyapi.NovaAPI.utils.ThreadMonitor;

public class NovaAPI {

    public static void printThreadInfo() {
        ThreadMonitor.logAllThreads();
    }

    public static void checkDeadlocks() {
        ThreadMonitor.checkForDeadlocks();
    }
}