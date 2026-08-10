package com.thunder.wildernessodysseyapi.debugoverlay;

import java.util.List;

/** Adds optional sections to an existing page without creating a hard mod dependency. */
@FunctionalInterface
public interface DebugPageContributor {
    List<DebugSection> contribute(DebugContext context);
}
