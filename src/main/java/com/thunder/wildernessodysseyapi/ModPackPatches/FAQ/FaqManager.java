package com.thunder.wildernessodysseyapi.ModPackPatches.FAQ;

import com.google.common.reflect.TypeToken;
import com.google.gson.Gson;
import com.google.gson.JsonElement;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;

import java.lang.reflect.Type;
import java.util.*;
import java.util.stream.Collectors;

public class FaqManager extends SimpleJsonResourceReloadListener {
    private static final Type FAQ_TYPE = new TypeToken<List<FaqEntry>>() {}.getType();
    private static final Map<String, FaqEntry> FAQ_ENTRIES = new HashMap<>();

    public FaqManager() {
        super(new Gson(), "faq");
    }

    /**
     * @param resourceLocationJsonElementMap
     * @param resourceManager
     * @param profilerFiller
     */
    @Override
    protected void apply(Map<ResourceLocation, JsonElement> resourceLocationJsonElementMap, ResourceManager resourceManager, ProfilerFiller profilerFiller) {

    }

    @Override
    protected void apply(Map<ResourceLocation, JsonElement> map, ResourceManager manager, MinecraftServer server) {
        FAQ_ENTRIES.clear();
        for (Map.Entry<ResourceLocation, JsonElement> entry : map.entrySet()) {
            try {
                FaqEntry[] entries = new Gson().fromJson(entry.getValue(), FaqEntry[].class);
                for (FaqEntry faq : entries) {
                    FAQ_ENTRIES.put(faq.id(), faq);
                }
            } catch (Exception e) {
                System.err.println("Failed to parse FAQ: " + entry.getKey() + ", error: " + e);
            }
        }
        System.out.println("Loaded " + FAQ_ENTRIES.size() + " FAQ entries.");
    }

    public static List<String> getIds() {
        return new ArrayList<>(FAQ_ENTRIES.keySet());
    }

    public static FaqEntry get(String id) {
        return FAQ_ENTRIES.get(id);
    }

    public static List<FaqEntry> search(String keyword) {
        return FAQ_ENTRIES.values().stream()
                .filter(entry -> entry.question().toLowerCase().contains(keyword.toLowerCase()))
                .toList();
    }

    public static List<FaqEntry> getByCategory(String category) {
        return FAQ_ENTRIES.values().stream()
                .filter(entry -> entry.category().equalsIgnoreCase(category))
                .collect(Collectors.toList());
    }

    public static Set<String> getCategories() {
        return FAQ_ENTRIES.values().stream().map(FaqEntry::category).collect(Collectors.toSet());
    }
}