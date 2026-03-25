package com.thunder.wildernessodysseyapi.ModPackPatches.faq;

import com.google.common.reflect.TypeToken;
import com.google.gson.Gson;
import com.google.gson.JsonElement;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;

import java.io.InputStreamReader;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Collectors;

import static com.thunder.wildernessodysseyapi.core.ModConstants.LOGGER;

/**
 * Loads and stores FAQ entries from resources.
 */
public class FaqManager {
    private static final Gson GSON = new Gson();
    private static final Type LIST_TYPE = new TypeToken<List<FaqEntry>>() {}.getType();

    private static final int MAX_ID_LENGTH = 64;
    private static final int MAX_QUESTION_LENGTH = 180;
    private static final int MAX_ANSWER_LENGTH = 1_500;

    private static final Map<String, FaqEntry> FAQ_ENTRIES = new HashMap<>();
    private static final Map<String, List<FaqEntry>> FAQ_BY_TOPIC = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
    private static final Map<String, Set<String>> FAQ_SEARCH_TOKENS = new HashMap<>();
    private static final Map<String, String> FAQ_TOPIC_BY_ID = new HashMap<>();
    private static final Map<String, Integer> NO_RESULT_QUERIES = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);

    /**
     * Reads all FAQ JSON files from the given resource manager.
     */
    public static void loadFromResources(ResourceManager manager) {
        Map<ResourceLocation, Resource> resources = manager.listResources("faq", path -> path.getPath().endsWith(".json"));
        Map<ResourceLocation, JsonElement> jsonMap = new HashMap<>();

        for (Map.Entry<ResourceLocation, Resource> entry : resources.entrySet()) {
            try (var reader = new InputStreamReader(entry.getValue().open())) {
                JsonElement element = GSON.fromJson(reader, JsonElement.class);
                jsonMap.put(entry.getKey(), element);
            } catch (Exception e) {
                LOGGER.error("Failed to parse FAQ {}", entry.getKey(), e);
            }
        }

        applyReloadData(jsonMap);
    }

    /**
     * Applies FAQ data from reload-listener JSON payloads.
     */
    public static void applyReloadData(Map<ResourceLocation, JsonElement> map) {
        clear();

        for (Map.Entry<ResourceLocation, JsonElement> entry : map.entrySet()) {
            String topic = topicFromResource(entry.getKey());
            try {
                List<FaqEntry> entries = GSON.fromJson(entry.getValue(), LIST_TYPE);
                if (entries == null || entries.isEmpty()) {
                    LOGGER.warn("FAQ file '{}' did not contain any entries", entry.getKey());
                    continue;
                }

                for (FaqEntry faqEntry : entries) {
                    add(faqEntry, topic);
                }
            } catch (Exception e) {
                LOGGER.error("Failed to parse FAQ {}", entry.getKey(), e);
            }
        }

        LOGGER.info("Loaded {} FAQ entries across {} topics.", FAQ_ENTRIES.size(), FAQ_BY_TOPIC.size());
    }

    /** Clears all loaded FAQs. */
    public static void clear() {
        FAQ_ENTRIES.clear();
        FAQ_BY_TOPIC.clear();
        FAQ_SEARCH_TOKENS.clear();
        FAQ_TOPIC_BY_ID.clear();
        NO_RESULT_QUERIES.clear();
    }

    /** Adds a single FAQ entry, preferring the topic inferred from the source file path. */
    public static void add(FaqEntry entry, String inferredTopic) {
        if (entry == null) {
            return;
        }

        String id = normalize(entry.id());
        String topic = normalize(inferredTopic);
        String legacyCategory = normalize(entry.category());
        if (topic.isEmpty()) {
            topic = legacyCategory;
        }

        if (!legacyCategory.isEmpty() && !legacyCategory.equalsIgnoreCase(topic)) {
            LOGGER.warn("FAQ '{}' category '{}' does not match topic file '{}'; using topic file", id, legacyCategory, topic);
        }

        if (id.isEmpty() || topic.isEmpty()) {
            LOGGER.warn("Skipping FAQ entry with missing id or topic: {}", entry);
            return;
        }

        if (id.length() > MAX_ID_LENGTH) {
            LOGGER.warn("Skipping FAQ '{}' because id length exceeded {} chars", id, MAX_ID_LENGTH);
            return;
        }
        if (normalize(entry.question()).isEmpty() || entry.question().length() > MAX_QUESTION_LENGTH) {
            LOGGER.warn("Skipping FAQ '{}' because question was blank or exceeded {} chars", id, MAX_QUESTION_LENGTH);
            return;
        }
        if (normalize(entry.answer()).isEmpty() || entry.answer().length() > MAX_ANSWER_LENGTH) {
            LOGGER.warn("Skipping FAQ '{}' because answer was blank or exceeded {} chars", id, MAX_ANSWER_LENGTH);
            return;
        }

        if (FAQ_ENTRIES.containsKey(id)) {
            LOGGER.warn("Duplicate FAQ id '{}' detected; latest entry will overwrite earlier one", id);
            FAQ_BY_TOPIC.values().forEach(list -> list.removeIf(existing -> id.equalsIgnoreCase(normalize(existing.id()))));
        }

        FAQ_ENTRIES.put(id, entry);
        FAQ_BY_TOPIC.computeIfAbsent(topic, c -> new ArrayList<>()).add(entry);
        FAQ_TOPIC_BY_ID.put(id, topic);
        FAQ_SEARCH_TOKENS.put(id, buildSearchTokens(entry, topic));
    }

    /** Returns all FAQ ids. */
    public static List<String> getIds() {
        return FAQ_ENTRIES.keySet().stream().sorted().toList();
    }

    /** Gets a FAQ entry by id. */
    public static FaqEntry get(String id) {
        return FAQ_ENTRIES.get(normalize(id));
    }

    /** Searches FAQs by keyword across all fields with multi-term ranking and fuzzy matching. */
    public static List<FaqEntry> search(String keyword) {
        String query = normalize(keyword).toLowerCase(Locale.ROOT);
        if (query.isEmpty()) {
            return Collections.emptyList();
        }

        List<String> terms = Arrays.stream(query.split("\\s+"))
                .map(FaqManager::normalize)
                .filter(s -> !s.isEmpty())
                .toList();

        List<FaqEntry> results = FAQ_ENTRIES.values().stream()
                .map(entry -> Map.entry(entry, relevanceScore(entry, query, terms)))
                .filter(scored -> scored.getValue() > 0)
                .sorted(Map.Entry.<FaqEntry, Integer>comparingByValue().reversed()
                        .thenComparing(scored -> scored.getKey().id(), String.CASE_INSENSITIVE_ORDER))
                .map(Map.Entry::getKey)
                .toList();

        if (results.isEmpty()) {
            NO_RESULT_QUERIES.merge(query, 1, Integer::sum);
            LOGGER.info("FAQ no-result query '{}' seen {} times", query, NO_RESULT_QUERIES.get(query));
        }

        return results;
    }

    private static int relevanceScore(FaqEntry entry, String query, List<String> terms) {
        String id = normalize(entry.id()).toLowerCase(Locale.ROOT);
        String question = toLower(entry.question());
        String answer = toLower(entry.answer());
        Set<String> aliases = entry.aliases() == null
                ? Collections.emptySet()
                : entry.aliases().stream().map(FaqManager::normalize).map(String::toLowerCase).collect(Collectors.toSet());

        String topic = normalize(FAQ_TOPIC_BY_ID.getOrDefault(id, ""));
        String combined = id + ' ' + topic + ' ' + question + ' ' + answer + ' ' + String.join(" ", aliases);
        Set<String> tokens = FAQ_SEARCH_TOKENS.getOrDefault(id, Collections.emptySet());

        int score = 0;
        int matchedTerms = 0;

        for (String term : terms) {
            boolean matched = false;
            if (id.contains(term)) {
                score += 14;
                matched = true;
            }
            if (topic.contains(term)) {
                score += 10;
                matched = true;
            }
            if (question.contains(term)) {
                score += 12;
                matched = true;
            }
            if (answer.contains(term)) {
                score += 7;
                matched = true;
            }
            if (aliases.stream().anyMatch(alias -> alias.contains(term))) {
                score += 11;
                matched = true;
            }
            if (tokens.contains(term)) {
                score += 9;
                matched = true;
            } else if (tokens.stream().anyMatch(token -> levenshtein(token, term) <= 2)) {
                score += 4;
                matched = true;
            }
            if (matched) {
                matchedTerms++;
            }
        }

        if (!terms.isEmpty() && matchedTerms == terms.size()) {
            score += 15; // AND-style bonus
        } else if (matchedTerms > 0) {
            score += 5; // OR-style fallback
        }

        if (combined.contains(query)) {
            score += 12; // exact phrase bonus
        }

        int questionDistance = levenshtein(question, query);
        if (questionDistance <= 2) {
            score += 3;
        }

        return score;
    }

    private static Set<String> buildSearchTokens(FaqEntry entry, String topic) {
        String aliases = entry.aliases() == null ? "" : String.join(" ", entry.aliases());
        return Arrays.stream((normalize(entry.id()) + ' ' + normalize(topic) + ' ' +
                normalize(entry.question()) + ' ' + normalize(entry.answer()) + ' ' + normalize(aliases))
                .toLowerCase(Locale.ROOT)
                .split("[^a-z0-9_]+"))
                .filter(token -> !token.isBlank())
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    private static String topicFromResource(ResourceLocation id) {
        String path = id.getPath();
        if (path.startsWith("faq/")) {
            path = path.substring("faq/".length());
        }
        if (path.endsWith(".json")) {
            path = path.substring(0, path.length() - 5);
        }
        return normalize(path).toLowerCase(Locale.ROOT);
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

    /** Returns FAQs that belong to the given topic. */
    public static List<FaqEntry> getByTopic(String topic) {
        return FAQ_BY_TOPIC.getOrDefault(normalize(topic), Collections.emptyList())
                .stream()
                .sorted(Comparator.comparing(FaqEntry::id, String.CASE_INSENSITIVE_ORDER))
                .toList();
    }

    /** Lists all available topics. */
    public static Set<String> getTopics() {
        return Collections.unmodifiableSet(FAQ_BY_TOPIC.keySet());
    }


    public static String getTopicForId(String id) {
        return FAQ_TOPIC_BY_ID.getOrDefault(normalize(id), "");
    }
    public static Map<String, Integer> getNoResultQueries() {
        return Collections.unmodifiableMap(NO_RESULT_QUERIES);
    }

    public static int getEntryCount() {
        return FAQ_ENTRIES.size();
    }
}
