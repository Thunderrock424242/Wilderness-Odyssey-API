package com.thunder.wildernessodysseyapi.ModPackPatches.FAQ;

import com.google.common.reflect.TypeToken;
import com.google.gson.Gson;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;

import java.io.InputStreamReader;
import java.lang.reflect.Type;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Loads and stores FAQ entries from resources.
 */
public class FaqManager {
    private static final Map<String, FaqEntry> FAQ_ENTRIES = new HashMap<>();

    /**
     * Reads all FAQ JSON files from the given resource manager.
     */
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

    /** Clears all loaded FAQs. */
    public static void clear() {
        FAQ_ENTRIES.clear();
    }

    /** Adds a single FAQ entry. */
    public static void add(FaqEntry entry) {
        FAQ_ENTRIES.put(entry.id(), entry);
    }

    /** Returns all FAQ ids. */
    public static List<String> getIds() {
        return new ArrayList<>(FAQ_ENTRIES.keySet());
    }

    /** Gets a FAQ entry by id. */
    public static FaqEntry get(String id) {
        return FAQ_ENTRIES.get(id);
    }

    /** Searches FAQs by keyword in the question text. */
    public static List<FaqEntry> search(String keyword) {
        return FAQ_ENTRIES.values().stream()
                .filter(entry -> entry.question().toLowerCase().contains(keyword.toLowerCase()))
                .toList();
    }

    /** Returns FAQs that belong to the given category. */
    public static List<FaqEntry> getByCategory(String category) {
        return FAQ_ENTRIES.values().stream()
                .filter(entry -> entry.category().equalsIgnoreCase(category))
                .toList();
    }

    /** Lists all available categories. */
    public static Set<String> getCategories() {
        return FAQ_ENTRIES.values().stream().map(FaqEntry::category).collect(Collectors.toSet());
    }
}
