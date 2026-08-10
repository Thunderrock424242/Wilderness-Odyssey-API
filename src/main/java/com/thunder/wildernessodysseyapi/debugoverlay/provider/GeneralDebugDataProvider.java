package com.thunder.wildernessodysseyapi.debugoverlay.provider;

import com.thunder.wildernessodysseyapi.debugoverlay.DebugContext;
import com.thunder.wildernessodysseyapi.debugoverlay.DebugSection;

import java.util.ArrayList;
import java.util.List;

/** Builds the intentionally small default page from focused provider summaries. */
public final class GeneralDebugDataProvider implements DebugDataProvider {
    private final WorldDebugDataProvider world = new WorldDebugDataProvider();
    private final TargetDebugDataProvider target = new TargetDebugDataProvider();

    @Override
    public List<DebugSection> collect(DebugContext context) {
        List<DebugSection> sections = new ArrayList<>(2);
        sections.addAll(world.summary(context));
        sections.addAll(target.summary(context));
        return List.copyOf(sections);
    }
}
