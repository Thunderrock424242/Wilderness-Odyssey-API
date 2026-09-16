package com.thunder.wildernessodysseyapi.performance.ram;

import java.lang.management.ManagementFactory;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class JvmArgumentInspector {
    private JvmArgumentInspector() {}

    public static List<String> xmxArguments() {
        List<String> result = new ArrayList<>();
        for (String arg : ManagementFactory.getRuntimeMXBean().getInputArguments()) {
            if (arg.toLowerCase(Locale.ROOT).startsWith("-xmx")) {
                result.add(arg);
            }
        }
        return List.copyOf(result);
    }

    public static List<String> xmsArguments() {
        List<String> result = new ArrayList<>();
        for (String arg : ManagementFactory.getRuntimeMXBean().getInputArguments()) {
            if (arg.toLowerCase(Locale.ROOT).startsWith("-xms")) {
                result.add(arg);
            }
        }
        return List.copyOf(result);
    }

    public static boolean hasDuplicateXmx() {
        return xmxArguments().size() > 1;
    }

    public static boolean hasDuplicateXms() {
        return xmsArguments().size() > 1;
    }
}
