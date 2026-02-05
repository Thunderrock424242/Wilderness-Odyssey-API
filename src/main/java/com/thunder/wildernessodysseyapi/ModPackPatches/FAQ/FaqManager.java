package com.thunder.wildernessodysseyapi.ModPackPatches.FAQ;

import com.google.common.reflect.TypeToken;
import com.google.gson.Gson;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;

import java.io.InputStreamReader;
import java.lang.reflect.Type;
import java.util.*;
import java.util.stream.Collectors;

import static com.thunder.wildernessodysseyapi.Core.ModConstants.LOGGER;
/**
 * Loads and stores FAQ entries from resources.
 */
public class FaqManager {
    private static final Map<String, FaqEntry> FAQ_ENTRIES = new HashMap<>();
    private static final Map<String, List<FaqEntry>> FAQ_BY_CATEGORY = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
    private static final Map<String, Set<String>> FAQ_SEARCH_TOKENS = new HashMap<>();
    /**
     * Reads all FAQ JSON files from the given resource manager.
     */
    public static void loadFromResources(ResourceManager manager) {
        clear();
        Gson gson = new Gson();
        Type listType = new TypeToken<List<FaqEntry>>() {}.getType();
        Map<ResourceLocation, Resource> resources = manager.listResources("faq", path -> path.getPath().endsWith(".json"));
        for (Map.Entry<ResourceLocation, Resource> entry : resources.entrySet()) {
            ResourceLocation id = entry.getKey();
            try (var reader = new InputStreamReader(entry.getValue().open())) {
                List<FaqEntry> entries = gson.fromJson(reader, listType);
                if (entries == null || entries.isEmpty()) {
                    LOGGER.warn("FAQ data file '{}' was empty", id);
                    continue;
                }
                for (FaqEntry faqEntry : entries) {
                    add(faqEntry);
                }
            } catch (Exception e) {
                LOGGER.error("Failed to parse FAQ {}", id, e);
            }
        }
        System.out.println("Loaded " + FAQ_ENTRIES.size() + " FAQ entries.");
    }
    /** Clears all loaded FAQs. */
    public static void clear() {
        FAQ_ENTRIES.clear();
        FAQ_BY_CATEGORY.clear();
        FAQ_SEARCH_TOKENS.clear();
    }
    /** Adds a single FAQ entry. */
    public static void add(FaqEntry entry) {
        if (entry == null) {
            return;
        }

        String id = normalize(entry.id());
        String category = normalize(entry.category());

        if (id.isEmpty() || category.isEmpty()) {
            LOGGER.warn("Skipping FAQ entry with missing id or category: {}", entry);
            return;
        }

        FAQ_ENTRIES.put(id, entry);
        FAQ_BY_CATEGORY.computeIfAbsent(category, c -> new ArrayList<>()).add(entry);
        FAQ_SEARCH_TOKENS.put(id, buildSearchTokens(entry));
    }
    /** Returns all FAQ ids. */
    public static List<String> getIds() {
        return FAQ_ENTRIES.keySet().stream().sorted().toList();
    }
    /** Gets a FAQ entry by id. */
    public static FaqEntry get(String id) {
        return FAQ_ENTRIES.get(normalize(id));
    }

    /** Searches FAQs by keyword across all fields with basic fuzzy matching. */
    public static List<FaqEntry> search(String keyword) {
        String lower = normalize(keyword).toLowerCase(Locale.ROOT);
        if (lower.isEmpty()) {
            return Collections.emptyList();
        }

        return FAQ_ENTRIES.values().stream()
                .map(entry -> Map.entry(entry, relevanceScore(entry, lower)))
                .filter(scored -> scored.getValue() > 0)
                .sorted(Map.Entry.<FaqEntry, Integer>comparingByValue().reversed()
                        .thenComparing(scored -> scored.getKey().id(), String.CASE_INSENSITIVE_ORDER))
                .map(Map.Entry::getKey)
                .toList();
    }

    private static int relevanceScore(FaqEntry entry, String query) {
        String id = normalize(entry.id());
        String category = normalize(entry.category());
        String question = toLower(entry.question());
        String answer = toLower(entry.answer());
        Set<String> tokens = FAQ_SEARCH_TOKENS.getOrDefault(id, Collections.emptySet());

        int score = 0;
        if (containsIgnoreCase(id, query)) score += 12;
        if (containsIgnoreCase(category, query)) score += 7;
        if (containsIgnoreCase(question, query)) score += 10;
        if (containsIgnoreCase(answer, query)) score += 6;

        if (tokens.contains(query)) {
            score += 11;
        } else {
            boolean fuzzyTokenMatch = tokens.stream().anyMatch(token -> levenshtein(token, query) <= 2);
            if (fuzzyTokenMatch) {
                score += 5;
            }
        }

        int questionDistance = levenshtein(question, query);
        if (questionDistance <= 2) {
            score += 4;
        }
        return score;
    }

    private static Set<String> buildSearchTokens(FaqEntry entry) {
        return Arrays.stream((normalize(entry.id()) + ' ' + normalize(entry.category()) + ' ' +
                normalize(entry.question()) + ' ' + normalize(entry.answer()))
                .toLowerCase(Locale.ROOT)
                .split("[^a-z0-9_]+"))
                .filter(token -> !token.isBlank())
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }
    private static boolean containsIgnoreCase(String text, String query) {
        if (text == null) {
            return false;
        }
        return text.toLowerCase(Locale.ROOT).contains(query);
    }
    private static String toLower(String value) {
        return value == null ? "" : value.toLowerCase(Locale.ROOT);
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim();
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
        return FAQ_BY_CATEGORY.getOrDefault(normalize(category), Collections.emptyList())
                .stream().sorted(Comparator.comparing(FaqEntry::id))
                .toList();
    }
    /** Lists all available categories. */
    public static Set<String> getCategories() {
        return Collections.unmodifiableSet(FAQ_BY_CATEGORY.keySet());
    }
}
