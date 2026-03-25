package com.thunder.wildernessodysseyapi.ModPackPatches.faq;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;

import java.util.Map;

/**
 * Reload listener that populates the FAQ manager when data packs reload.
 */
public class FaqReloadListener extends SimpleJsonResourceReloadListener {
    private static final Gson GSON = new Gson();

    public FaqReloadListener() {
        super(GSON, "faq");
    }

    /**
     * Parses FAQ entries from JSON and stores them in {@link FaqManager}.
     */
    @Override
    protected void apply(Map<ResourceLocation, JsonElement> map, ResourceManager resourceManager, ProfilerFiller profiler) {
        FaqManager.applyReloadData(map);
    }
}
