package com.thunder.wildernessodysseyapi.AI_perf;

import java.util.ArrayList;
import java.util.List;

import com.thunder.wildernessodysseyapi.AI_perf.PerformanceMitigationController;

/**
 * Simple client that reads lore from {@code ai_config.yaml} and
 * echoes player messages. This acts as a placeholder for a future
 * networked AI service.
 */
public class requestperfadvice {

    private final List<String> story = new ArrayList<>();
    private final MemoryStore memoryStore = new MemoryStore();

    /**
     * Builds a formatted advisory request for performance-heavy systems and returns a
     * deterministic response. This keeps AI usage optional while providing a consistent
     * prompt format for future networked helpers.
     *
     * @param request snapshot of the heavy systems
     * @return canned advisory response
     */
    public String requestPerformanceAdvice(PerformanceAdvisoryRequest request) {
        String prompt = PerformanceAdvisor.buildPrompt(request);
        String reply = PerformanceAdvisor.buildLocalAdvice(request);
        String worldKey = "server";
        String assistantId = "advisor";
        memoryStore.addMessage(worldKey, assistantId, prompt);
        memoryStore.addMessage(worldKey, assistantId, reply);
        return reply;
    }
}

