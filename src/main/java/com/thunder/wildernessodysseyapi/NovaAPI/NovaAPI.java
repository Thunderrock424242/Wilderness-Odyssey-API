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

///// once our helper the one we payed fixes our code and improves it lets try to ship nova api as a seperate mod. note for thunder: maybe ask him if we can add him as a contributer on curseforge for his hard work.