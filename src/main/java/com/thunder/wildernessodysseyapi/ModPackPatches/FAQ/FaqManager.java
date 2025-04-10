package com.thunder.wildernessodysseyapi.ModPackPatches.FAQ;

import com.google.common.reflect.TypeToken;
import com.google.gson.Gson;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;

import java.io.InputStreamReader;
import java.lang.reflect.Type;
import java.util.*;
import java.util.stream.Collectors;

public class FaqManager {
    private static final Map<String, FaqEntry> FAQ_ENTRIES = new HashMap<>();

    public static void loadFromResources(ResourceManager manager) {
        FAQ_ENTRIES.clear();
        Gson gson = new Gson();
        Type listType = new TypeToken<List<FaqEntry>>() {}.getType();

        for (ResourceLocation id : manager.listResources("faq", path -> path.getPath().endsWith(".json")).keySet()) {
            try (var reader = new InputStreamReader(manager.getResource(id).get().open())) {
                List<FaqEntry> entries = gson.fromJson(reader, listType);
                for (FaqEntry entry : entries) {
                    FAQ_ENTRIES.put(entry.id(), entry);
                }
            } catch (Exception e) {
                System.err.println("Failed to parse FAQ: " + id + ", error: " + e);
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
                .toList();
    }

    public static Set<String> getCategories() {
        return FAQ_ENTRIES.values().stream().map(FaqEntry::category).collect(Collectors.toSet());
    }
}