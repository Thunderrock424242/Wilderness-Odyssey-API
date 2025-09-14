package com.thunder.wildernessodysseyapi.ModPackPatches.FAQ;
import com.google.common.reflect.TypeToken;
import com.google.gson.Gson;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import java.io.InputStreamReader;
import java.lang.reflect.Type;
import java.util.*;
/**
 * Loads and stores FAQ entries from resources.
 */
public class FaqManager {
    private static final Map<String, FaqEntry> FAQ_ENTRIES = new HashMap<>();
    private static final Map<String, List<FaqEntry>> FAQ_BY_CATEGORY = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
    /**
     * Reads all FAQ JSON files from the given resource manager.
     */
    public static void loadFromResources(ResourceManager manager) {
        clear();
        Gson gson = new Gson();
        Type listType = new TypeToken<List<FaqEntry>>() {}.getType();
        for (ResourceLocation id : manager.listResources("faq", path -> path.getPath().endsWith(".json")).keySet()) {
            try (var reader = new InputStreamReader(manager.getResource(id).get().open())) {
                List<FaqEntry> entries = gson.fromJson(reader, listType);
                for (FaqEntry entry : entries) {
                    add(entry);
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
        FAQ_BY_CATEGORY.clear();
    }
    /** Adds a single FAQ entry. */
    public static void add(FaqEntry entry) {
        FAQ_ENTRIES.put(entry.id(), entry);
        FAQ_BY_CATEGORY.computeIfAbsent(entry.category(), c -> new ArrayList<>()).add(entry);
    }
    /** Returns all FAQ ids. */
    public static List<String> getIds() {
        return FAQ_ENTRIES.keySet().stream().sorted().toList();
    }
    /** Gets a FAQ entry by id. */
    public static FaqEntry get(String id) {
        return FAQ_ENTRIES.get(id);
    }

    /** Searches FAQs by keyword across all fields with basic fuzzy matching. */
    public static List<FaqEntry> search(String keyword) {
        String lower = keyword.toLowerCase();
        return FAQ_ENTRIES.values().stream()
                .filter(entry ->
                        containsIgnoreCase(entry.id(), lower) ||
                        containsIgnoreCase(entry.category(), lower) ||
                        containsIgnoreCase(entry.question(), lower) ||
                        containsIgnoreCase(entry.answer(), lower) ||
                        levenshtein(entry.question().toLowerCase(), lower) <= 2)
                .toList();
    }
    private static boolean containsIgnoreCase(String text, String query) {
        return text.toLowerCase().contains(query);
    }
    private static int levenshtein(String a, String b) {
        int[] costs = new int[b.length() + 1];
        for (int j = 0; j < costs.length; j++) {
            costs[j] = j;
        }
        for (int i = 1; i <= a.length(); i++) {
            costs[0] = i;
            int nw = i - 1;
            for (int j = 1; j <= b.length(); j++) {
                int cj = Math.min(1 + Math.min(costs[j], costs[j - 1]),
                        a.charAt(i - 1) == b.charAt(j - 1) ? nw : nw + 1);
                nw = costs[j];
                costs[j] = cj;
            }
        }
        return costs[b.length()];
    }

    /** Returns FAQs that belong to the given category. */
    public static List<FaqEntry> getByCategory(String category) {
        return FAQ_BY_CATEGORY.getOrDefault(category, Collections.emptyList())
                .stream().sorted(Comparator.comparing(FaqEntry::id))
                .toList();
    }
    /** Lists all available categories. */
    public static Set<String> getCategories() {
        return Collections.unmodifiableSet(FAQ_BY_CATEGORY.keySet());
    }
}