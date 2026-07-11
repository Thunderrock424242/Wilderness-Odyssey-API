package com.thunder.wildernessodysseyapi.watersystem.water.compat;

import com.thunder.wildernessodysseyapi.core.ModConstants;
import com.thunder.wildernessodysseyapi.watersystem.water.compat.vanilla.EntityWaterCompat;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Owns compatibility adapter discovery and one-time initialization.
 *
 * <p>Future mod-specific bootstraps should check their mod ID before creating an
 * adapter that references optional classes. Core water packages never inspect
 * optional mods and never classload their APIs.</p>
 */
public final class WaterCompatibilityRegistry {

    private static final Map<String, WaterCompatibilityAdapter> ADAPTERS = new LinkedHashMap<>();
    private static final List<String> INITIALIZED = new ArrayList<>();
    private static boolean bootstrapped;

    private WaterCompatibilityRegistry() {
    }

    /** Registers built-in adapters and initializes those available at runtime. */
    public static synchronized void bootstrap() {
        if (bootstrapped) {
            return;
        }
        bootstrapped = true;

        // Entity state is the first proof adapter because swimming, drowning,
        // optics, audio, boats, and mob behavior can all consume its one cache.
        register(new EntityWaterCompat());

        for (WaterCompatibilityAdapter adapter : ADAPTERS.values()) {
            if (!adapter.isAvailable()) {
                continue;
            }
            adapter.initialize();
            INITIALIZED.add(adapter.id());
            ModConstants.LOGGER.info(
                    "Initialized water compatibility adapter {} at {} support",
                    adapter.id(),
                    adapter.compatibilityLevel()
            );
        }
    }

    /** Adds one uniquely identified adapter before bootstrap completes. */
    public static synchronized void register(WaterCompatibilityAdapter adapter) {
        WaterCompatibilityAdapter previous = ADAPTERS.putIfAbsent(adapter.id(), adapter);
        if (previous != null) {
            throw new IllegalStateException("Duplicate water compatibility adapter: " + adapter.id());
        }
    }

    /** Returns an immutable diagnostic snapshot of known adapters. */
    public static synchronized List<AdapterStatus> statuses() {
        List<AdapterStatus> statuses = new ArrayList<>(ADAPTERS.size());
        for (WaterCompatibilityAdapter adapter : ADAPTERS.values()) {
            statuses.add(new AdapterStatus(
                    adapter.id(),
                    adapter.compatibilityLevel(),
                    adapter.isAvailable(),
                    INITIALIZED.contains(adapter.id())
            ));
        }
        return Collections.unmodifiableList(statuses);
    }

    /** Compact adapter state suitable for commands and future debug UI. */
    public record AdapterStatus(
            String id,
            CompatibilityLevel compatibilityLevel,
            boolean available,
            boolean initialized
    ) {
    }
}
