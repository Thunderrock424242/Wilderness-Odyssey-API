package com.thunder.wildernessodysseyapi.debugoverlay.provider;

import com.thunder.wildernessodysseyapi.debugoverlay.DebugContext;
import com.thunder.wildernessodysseyapi.debugoverlay.DebugSection;

import java.util.List;

/** Collects the ordered sections for a debug page without owning rendering state. */
@FunctionalInterface
public interface DebugDataProvider {
    /** Collects page data from the supplied client render context. */
    List<DebugSection> collect(DebugContext context);
}
