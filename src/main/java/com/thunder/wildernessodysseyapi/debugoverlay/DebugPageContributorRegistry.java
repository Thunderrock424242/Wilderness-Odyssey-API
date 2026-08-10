package com.thunder.wildernessodysseyapi.debugoverlay;

import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Registry for optional Iris, distant-renderer, water, and future subsystem sections.
 *
 * <p>Integrations register callbacks during client setup. A failing callback is
 * isolated to an unavailable row so an optional provider cannot take down F3.</p>
 */
public final class DebugPageContributorRegistry {
    private static final Map<ResourceLocation, List<DebugPageContributor>> CONTRIBUTORS = new LinkedHashMap<>();

    private DebugPageContributorRegistry() {
    }

    /** Registers one optional section callback for a page. */
    public static synchronized void register(ResourceLocation pageId, DebugPageContributor contributor) {
        CONTRIBUTORS.computeIfAbsent(Objects.requireNonNull(pageId), ignored -> new ArrayList<>())
                .add(Objects.requireNonNull(contributor));
    }

    /** Invokes the callbacks registered for the selected page. */
    public static List<DebugSection> collect(ResourceLocation pageId, DebugContext context) {
        List<DebugPageContributor> contributors;
        synchronized (DebugPageContributorRegistry.class) {
            List<DebugPageContributor> registered = CONTRIBUTORS.get(pageId);
            if (registered == null || registered.isEmpty()) {
                return List.of();
            }
            contributors = List.copyOf(registered);
        }

        List<DebugSection> sections = new ArrayList<>();
        for (DebugPageContributor contributor : contributors) {
            try {
                sections.addAll(contributor.contribute(context));
            } catch (RuntimeException exception) {
                sections.add(DebugSection.builder("INTEGRATION")
                        .add("Provider", DebugValue.unavailable(exception.getClass().getSimpleName()))
                        .build());
            }
        }
        return List.copyOf(sections);
    }
}
