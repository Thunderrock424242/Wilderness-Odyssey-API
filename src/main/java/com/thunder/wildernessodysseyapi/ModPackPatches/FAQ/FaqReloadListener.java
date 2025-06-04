package com.thunder.wildernessodysseyapi.ModPackPatches.FAQ;

import com.google.common.reflect.TypeToken;
import com.google.gson.Gson;
import com.google.gson.JsonElement;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;

import java.lang.reflect.Type;
import java.util.List;
import java.util.Map;

public class FaqReloadListener extends SimpleJsonResourceReloadListener {
    private static final Gson GSON = new Gson();
    private static final Type LIST_TYPE = new TypeToken<List<FaqEntry>>() {}.getType();

    public FaqReloadListener() {
        super(GSON, "faq");
    }

    @Override
    protected void apply(Map<ResourceLocation, JsonElement> map, ResourceManager resourceManager, ProfilerFiller profiler) {
        FaqManager.clear();
        for (Map.Entry<ResourceLocation, com.google.gson.JsonElement> entry : map.entrySet()) {
            try {
                List<FaqEntry> entries = GSON.fromJson(entry.getValue(), LIST_TYPE);
                for (FaqEntry entryObj : entries) {
                    FaqManager.add(entryObj);
                }
            } catch (Exception e) {
                System.err.println("Failed to parse FAQ: " + entry.getKey() + " → " + e.getMessage());
            }
        }
    }
}