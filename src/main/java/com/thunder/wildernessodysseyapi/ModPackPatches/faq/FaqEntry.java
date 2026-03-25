package com.thunder.wildernessodysseyapi.ModPackPatches.faq;

import java.util.List;

/**
 * FAQ record loaded from datapacks.
 *
 * @param id FAQ identifier used with /faq view
 * @param category legacy field retained for backwards compatibility; topic files are preferred
 * @param question short question shown in /faq list and /faq search
 * @param answer detailed answer shown in /faq view
 * @param aliases optional alternate terms used for search matching
 */
public record FaqEntry(String id, String category, String question, String answer, List<String> aliases) {
}
