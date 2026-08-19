package com.thunder.wildernessodysseyapi.debugoverlay;

import com.thunder.wildernessodysseyapi.debugoverlay.pages.GeneralDebugPage;
import com.thunder.wildernessodysseyapi.debugoverlay.pages.NetworkDebugPage;
import com.thunder.wildernessodysseyapi.debugoverlay.pages.PerformanceDebugPage;
import com.thunder.wildernessodysseyapi.debugoverlay.pages.RenderingDebugPage;
import com.thunder.wildernessodysseyapi.debugoverlay.pages.SystemDebugPage;
import com.thunder.wildernessodysseyapi.debugoverlay.pages.TargetDebugPage;
import com.thunder.wildernessodysseyapi.debugoverlay.pages.VanillaRawDebugPage;
import com.thunder.wildernessodysseyapi.debugoverlay.pages.WorldDebugPage;
import com.thunder.wildernessodysseyapi.dataengine.debug.client.DataEngineDebugPage;
import net.minecraft.resources.ResourceLocation;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Ordered registry for built-in and future Wilderness debug pages.
 *
 * <p>Future systems can call {@code DebugPageRegistry.register(new WaterDebugPage())}
 * during client setup; the manager and renderer require no corresponding edit.</p>
 */
public final class DebugPageRegistry {
    private static final Map<ResourceLocation, DebugPage> PAGES = new LinkedHashMap<>();
    private static boolean bootstrapped;

    private DebugPageRegistry() {
    }

    /** Registers the nine built-in pages exactly once. */
    public static synchronized void bootstrapDefaults() {
        if (bootstrapped) {
            return;
        }
        bootstrapped = true;
        registerInternal(new GeneralDebugPage());
        registerInternal(new WorldDebugPage());
        registerInternal(new PerformanceDebugPage());
        registerInternal(new RenderingDebugPage());
        registerInternal(new SystemDebugPage());
        registerInternal(new NetworkDebugPage());
        registerInternal(new DataEngineDebugPage());
        registerInternal(new TargetDebugPage());
        registerInternal(new VanillaRawDebugPage());
    }

    /** Appends a future page after ensuring the built-ins exist. */
    public static synchronized void register(DebugPage page) {
        bootstrapDefaults();
        registerInternal(page);
    }

    /** Returns an immutable snapshot in navigation order. */
    public static synchronized List<DebugPage> pages() {
        bootstrapDefaults();
        return List.copyOf(PAGES.values());
    }

    /** Returns the current number of registered pages. */
    public static synchronized int size() {
        bootstrapDefaults();
        return PAGES.size();
    }

    private static void registerInternal(DebugPage page) {
        Objects.requireNonNull(page, "Debug page is required");
        DebugPage previous = PAGES.putIfAbsent(page.id(), page);
        if (previous != null) {
            throw new IllegalArgumentException("A debug page is already registered for " + page.id());
        }
    }
}
