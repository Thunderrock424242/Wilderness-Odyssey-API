package com.thunder.wildernessodysseyapi.quest.validation;

import com.thunder.wildernessodysseyapi.quest.definition.ObjectiveDefinition;
import com.thunder.wildernessodysseyapi.quest.definition.QuestCampaignSnapshot;
import com.thunder.wildernessodysseyapi.quest.definition.RewardDefinition;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/** Validates references and bounded directed graphs without recursion or world access. */
public final class QuestValidator {
    public QuestValidationReport validate(QuestCampaignSnapshot candidate, QuestContentLookup lookup) {
        List<QuestValidationReport.Finding> findings = new ArrayList<>();
        var campaign = candidate.campaign();
        unique(campaign.id(), "chapters", campaign.chapters(), findings);
        for (var chapterId : campaign.chapters()) {
            if (!candidate.chapters().containsKey(chapterId)) {
                error(findings, campaign.id(), "chapters", "A referenced chapter is missing: " + chapterId);
            }
        }
        for (var chapter : candidate.chapters().values()) {
            if (!chapter.campaign().equals(campaign.id()) || !campaign.chapters().contains(chapter.id())) {
                error(findings, chapter.id(), "campaign", "Chapter membership does not match its campaign.");
            }
            unique(chapter.id(), "quests", chapter.quests(), findings);
            for (var questId : chapter.quests()) {
                var quest = candidate.quests().get(questId);
                if (quest == null || !quest.chapter().equals(chapter.id())) {
                    error(findings, chapter.id(), "quests", "Quest membership does not match this chapter: " + questId);
                }
            }
            for (String mod : chapter.requiredMods()) {
                if (!hasMod(lookup, mod)) {
                    error(findings, chapter.id(), "requiredMods", "A required mod is unavailable: " + mod);
                }
            }
            if (chapter.disabled()) {
                findings.add(new QuestValidationReport.Finding(QuestValidationReport.Severity.WARNING,
                        chapter.id(), "optionalMods", "This chapter is disabled because an optional mod is unavailable."));
            }
        }
        Map<ResourceLocation, List<ResourceLocation>> graph = new HashMap<>();
        for (var quest : candidate.quests().values()) {
            var chapter = candidate.chapters().get(quest.chapter());
            if (chapter == null || !chapter.quests().contains(quest.id())) {
                error(findings, quest.id(), "chapter", "Quest membership does not match its chapter.");
            }
            unique(quest.id(), "prerequisites.quests", quest.prerequisites(), findings);
            graph.put(quest.id(), quest.prerequisites());
            for (var prerequisite : quest.prerequisites()) {
                if (!candidate.quests().containsKey(prerequisite)) {
                    error(findings, quest.id(), "prerequisites.quests", "A prerequisite quest is missing: " + prerequisite);
                }
            }
            unique(quest.id(), "objectives.id", quest.objectives().stream().map(ObjectiveDefinition::id).toList(), findings);
            unique(quest.id(), "rewards.id", quest.rewards().stream().map(RewardDefinition::id).toList(), findings);
            Map<ResourceLocation, List<ResourceLocation>> objectiveGraph = new HashMap<>();
            for (var objective : quest.objectives()) {
                objectiveGraph.put(objective.id(), objective.dependsOn());
                unique(quest.id(), "objectives." + objective.id() + ".dependsOn", objective.dependsOn(), findings);
            }
            for (var objective : quest.objectives()) {
                for (var dependency : objective.dependsOn()) {
                    if (!objectiveGraph.containsKey(dependency)) {
                        error(findings, quest.id(), "objectives." + objective.id() + ".dependsOn", "An objective dependency is missing: " + dependency);
                    }
                }
                if (chapter == null || !chapter.disabled()) {
                    availability(quest.id(), objective, lookup, findings);
                }
            }
            if (cyclic(objectiveGraph)) {
                error(findings, quest.id(), "objectives.dependsOn", "Objective dependencies contain a cycle.");
            }
            if (chapter == null || !chapter.disabled()) {
                available(quest.id(), "icon", QuestContentLookup.ContentKind.ITEM, quest.icon(), lookup, findings);
                for (var reward : quest.rewards()) {
                    String path = "rewards." + reward.id();
                    switch (reward.value()) {
                        case RewardDefinition.Item item -> available(quest.id(), path + ".item", QuestContentLookup.ContentKind.ITEM, item.item(), lookup, findings);
                        case RewardDefinition.Lore lore -> available(quest.id(), path + ".lore", QuestContentLookup.ContentKind.LORE, lore.lore(), lookup, findings);
                        case RewardDefinition.Experience ignored -> { }
                    }
                }
            }
        }
        if (cyclic(graph)) {
            error(findings, campaign.id(), "quests.prerequisites", "Quest prerequisites contain a cycle.");
        }
        return new QuestValidationReport(Optional.of(candidate), findings);
    }

    public static boolean hasMod(QuestContentLookup lookup, String mod) {
        return lookup.contains(QuestContentLookup.ContentKind.MOD, ResourceLocation.fromNamespaceAndPath(mod, "loaded"));
    }

    private void availability(ResourceLocation quest, ObjectiveDefinition objective, QuestContentLookup lookup,
                              List<QuestValidationReport.Finding> findings) {
        String path = "objectives." + objective.id();
        switch (objective.parameters()) {
            case ObjectiveDefinition.Possession possession -> {
                if (possession.items().isEmpty() && possession.tags().isEmpty()) {
                    error(findings, quest, path, "Possession needs at least one item or item tag.");
                }
                for (var item : possession.items()) {
                    available(quest, path + ".items", QuestContentLookup.ContentKind.ITEM, item, lookup, findings);
                }
                for (var tag : possession.tags()) {
                    if (lookup.tagMembers(tag).isEmpty()) {
                        error(findings, quest, path + ".tags", "An item tag is missing or empty: " + tag);
                    }
                }
            }
            case ObjectiveDefinition.Dimension dimension -> available(quest, path + ".dimension", QuestContentLookup.ContentKind.DIMENSION, dimension.dimension(), lookup, findings);
            case ObjectiveDefinition.Lore lore -> available(quest, path + ".lore", QuestContentLookup.ContentKind.LORE, lore.lore(), lookup, findings);
            case ObjectiveDefinition.CustomEvent custom -> {
                if (!lookup.supportsEvent(custom.producer())) {
                    error(findings, quest, path + ".producer", "This event has no supported server producer: " + custom.producer());
                }
            }
        }
    }

    private static void available(ResourceLocation id, String path, QuestContentLookup.ContentKind kind,
                                  ResourceLocation target, QuestContentLookup lookup, List<QuestValidationReport.Finding> findings) {
        if (!lookup.contains(kind, target)) {
            error(findings, id, path, "Referenced content is unavailable: " + target);
        }
    }

    private static void unique(ResourceLocation id, String path, List<ResourceLocation> values,
                               List<QuestValidationReport.Finding> findings) {
        if (new HashSet<>(values).size() != values.size()) {
            error(findings, id, path, "IDs must not be duplicated within this list.");
        }
    }

    private static boolean cyclic(Map<ResourceLocation, List<ResourceLocation>> graph) {
        Map<ResourceLocation, Integer> remaining = new HashMap<>();
        Map<ResourceLocation, List<ResourceLocation>> dependents = new HashMap<>();
        for (var entry : graph.entrySet()) {
            Set<ResourceLocation> known = new HashSet<>(entry.getValue());
            known.retainAll(graph.keySet());
            remaining.put(entry.getKey(), known.size());
            known.forEach(id -> dependents.computeIfAbsent(id, ignored -> new ArrayList<>()).add(entry.getKey()));
        }
        ArrayDeque<ResourceLocation> ready = new ArrayDeque<>();
        remaining.forEach((id, count) -> { if (count == 0) ready.add(id); });
        int visited = 0;
        while (!ready.isEmpty()) {
            var id = ready.removeFirst();
            visited++;
            for (var dependent : dependents.getOrDefault(id, List.of())) {
                if (remaining.compute(dependent, (ignored, count) -> count - 1) == 0) {
                    ready.addLast(dependent);
                }
            }
        }
        return visited != graph.size();
    }

    public static void error(List<QuestValidationReport.Finding> findings, ResourceLocation id, String path, String message) {
        findings.add(new QuestValidationReport.Finding(QuestValidationReport.Severity.ERROR, id, path, message));
    }
}
