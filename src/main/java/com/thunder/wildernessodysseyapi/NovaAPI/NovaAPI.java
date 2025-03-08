package com.thunder.wildernessodysseyapi.NovaAPI;

import com.thunder.wildernessodysseyapi.NovaAPI.utils.ThreadMonitor;

public class NovaAPI {

    public static void initialize() {
        ThreadMonitor.startMonitoring(); // Start automatic monitoring
    }

    public static void shutdown() {
        ThreadMonitor.stopMonitoring(); // Stop monitoring on game exit
    }
}