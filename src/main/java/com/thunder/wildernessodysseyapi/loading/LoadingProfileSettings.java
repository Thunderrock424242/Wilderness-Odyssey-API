package com.thunder.wildernessodysseyapi.loading;

import java.util.concurrent.TimeUnit;

/** Keeps the legacy JVM threshold formats while bounding diagnostic intervals. */
public final class LoadingProfileSettings {
    private LoadingProfileSettings() {
    }

    public static long reportDelayMillis(String override) {
        long minutes = 1;
        try {
            String value = override.trim();
            if (value.endsWith("t")) {
                minutes = (long) Math.ceil(Long.parseLong(value.substring(0, value.length() - 1)) / 1200.0);
            } else if (value.contains(":")) {
                String[] parts = value.split(":", -1);
                if (parts.length < 2 || parts.length > 3) {
                    return TimeUnit.MINUTES.toMillis(1);
                }
                long seconds = 0;
                for (String part : parts) {
                    long segment = Long.parseLong(part);
                    if (segment < 0) {
                        return TimeUnit.MINUTES.toMillis(1);
                    }
                    seconds = Math.addExact(Math.multiplyExact(seconds, 60), segment);
                }
                minutes = (long) Math.ceil(seconds / 60.0);
            } else if (!value.isEmpty()) {
                minutes = Long.parseLong(value);
            }
        } catch (IllegalArgumentException | ArithmeticException ignored) {
            minutes = 1;
        }
        return TimeUnit.MINUTES.toMillis(Math.max(1, Math.min(60, minutes)));
    }
}
