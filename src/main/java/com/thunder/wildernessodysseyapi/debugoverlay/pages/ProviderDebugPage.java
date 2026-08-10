package com.thunder.wildernessodysseyapi.debugoverlay.pages;

import com.thunder.wildernessodysseyapi.debugoverlay.DebugContext;
import com.thunder.wildernessodysseyapi.debugoverlay.DebugPage;
import com.thunder.wildernessodysseyapi.debugoverlay.DebugSection;
import com.thunder.wildernessodysseyapi.debugoverlay.provider.DebugDataProvider;
import net.minecraft.resources.ResourceLocation;

import java.time.Duration;
import java.util.List;
import java.util.Objects;

/**
 * Debug page backed by a provider with a small time-based result cache.
 *
 * <p>The manager invokes only the selected page, and this cache further avoids
 * rebuilding strings every rendered frame when values change more slowly.</p>
 */
public class ProviderDebugPage implements DebugPage {
    private final ResourceLocation id;
    private final String displayName;
    private final DebugDataProvider provider;
    private final long refreshNanos;

    private List<DebugSection> cachedSections = List.of();
    private long lastRefreshNanos = Long.MIN_VALUE;

    protected ProviderDebugPage(ResourceLocation id, String displayName, Duration refreshInterval, DebugDataProvider provider) {
        this.id = Objects.requireNonNull(id);
        this.displayName = Objects.requireNonNull(displayName);
        this.provider = Objects.requireNonNull(provider);
        this.refreshNanos = Math.max(0L, refreshInterval.toNanos());
    }

    @Override
    public final ResourceLocation id() {
        return id;
    }

    @Override
    public final String displayName() {
        return displayName;
    }

    @Override
    public List<DebugSection> sections(DebugContext context) {
        if (cachedSections.isEmpty()
                || refreshNanos == 0L
                || context.capturedNanos() - lastRefreshNanos >= refreshNanos) {
            cachedSections = List.copyOf(provider.collect(context));
            lastRefreshNanos = context.capturedNanos();
        }
        return cachedSections;
    }
}
