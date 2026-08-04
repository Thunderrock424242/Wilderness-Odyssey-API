package com.thunder.wildernessodysseyapi.ecosystem.data;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.thunder.wildernessodysseyapi.ecosystem.EcosystemEvents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import net.neoforged.neoforge.server.ServerLifecycleHooks;

import java.util.Map;

/** Loads ecosystem species profiles from {@code data/<namespace>/ecosystem/species}. */
public final class SpeciesBehaviorProfileReloadListener extends SimpleJsonResourceReloadListener {

    private static final Gson GSON = new Gson();

    public SpeciesBehaviorProfileReloadListener() {
        super(GSON, "ecosystem/species");
    }

    /** Atomically replaces the active profile generation after data-pack preparation completes. */
    @Override
    protected void apply(
            Map<ResourceLocation, JsonElement> resources,
            ResourceManager resourceManager,
            ProfilerFiller profiler
    ) {
        SpeciesBehaviorProfileManager.apply(resources);
        var server = ServerLifecycleHooks.getCurrentServer();
        if (server != null) {
            server.execute(() -> EcosystemEvents.refreshLoadedControllers(server));
        }
    }
}
