package com.thunder.wildernessodysseyapi.quest.runtime;

import com.thunder.wildernessodysseyapi.quest.definition.ObjectiveDefinition;
import com.thunder.wildernessodysseyapi.quest.definition.QuestCampaignSnapshot;
import com.thunder.wildernessodysseyapi.quest.definition.QuestDefinition;
import com.thunder.wildernessodysseyapi.quest.objective.ItemObservation;
import com.thunder.wildernessodysseyapi.quest.objective.QuestEvent;
import com.thunder.wildernessodysseyapi.quest.objective.QuestObjectiveHandler;
import com.thunder.wildernessodysseyapi.quest.progress.QuestPlayerProgress;
import com.thunder.wildernessodysseyapi.quest.world.QuestWorldState;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Compiled pure evaluator. Indexed evidence visits affected quests, never every campaign quest per tick. */
public final class QuestObjectiveEngine {
    private final QuestCampaignSnapshot snapshot;
    private final Map<ResourceLocation, Set<ResourceLocation>> items = new HashMap<>();
    private final Map<ResourceLocation, Set<ResourceLocation>> tags = new HashMap<>();
    private final Map<ResourceLocation, Set<ResourceLocation>> dimensions = new HashMap<>();
    private final Map<ResourceLocation, Set<ResourceLocation>> lore = new HashMap<>();
    private final Map<ResourceLocation, Set<ResourceLocation>> custom = new HashMap<>();
    private final Map<ResourceLocation, Set<ResourceLocation>> dependents = new HashMap<>();

    private QuestObjectiveEngine(QuestCampaignSnapshot snapshot) {
        this.snapshot = snapshot;
        for (var quest : snapshot.quests().values()) {
            for (var prerequisite : quest.prerequisites()) index(dependents, prerequisite, quest.id());
            for (var objective : quest.objectives()) {
                switch (objective.parameters()) {
                    case ObjectiveDefinition.Possession possession -> {
                        possession.items().forEach(item -> index(items, item, quest.id()));
                        possession.tags().forEach(tag -> index(tags, tag, quest.id()));
                    }
                    case ObjectiveDefinition.Dimension dimension -> index(dimensions, dimension.dimension(), quest.id());
                    case ObjectiveDefinition.Lore record -> index(lore, record.lore(), quest.id());
                    case ObjectiveDefinition.CustomEvent event -> index(custom, event.producer(), quest.id());
                }
            }
        }
    }

    public static QuestObjectiveEngine compile(QuestCampaignSnapshot snapshot) {
        return new QuestObjectiveEngine(snapshot);
    }

    /** Recomputed only at login/publication/progress transitions, then cached by the runtime. */
    public boolean hasInventoryInterest(QuestPlayerProgress player) {
        var candidates = new HashSet<net.minecraft.resources.ResourceLocation>();
        items.values().forEach(candidates::addAll); tags.values().forEach(candidates::addAll);
        return candidates.stream().map(snapshot.quests()::get).anyMatch(quest -> eligible(quest, player.activeRuns(), player.runs())
                && !player.completed(quest.id()) && quest.objectives().stream().anyMatch(objective -> objective.parameters() instanceof ObjectiveDefinition.Possession
                && player.count(quest.id(), objective.id()) < objective.goal()));
    }

    public QuestTransitionBatch apply(QuestPlayerProgress player, QuestWorldState world, QuestEvent event) {
        if (event.evidence() instanceof QuestEvent.SharedFact fact) {
            var facts = new HashSet<>(world.facts());
            if (!facts.add(fact.fact())) return QuestTransitionBatch.unchanged(player, world);
            return QuestTransitionBatch.unchanged(player, new QuestWorldState(world.worldId(), facts));
        }
        if (!player.playerId().equals(event.actor())) return QuestTransitionBatch.unchanged(player, world);
        var occurrence = new QuestPlayerProgress.Occurrence(event.serverSession(), event.occurrenceId());
        if (player.recentOccurrences().contains(occurrence)) return QuestTransitionBatch.unchanged(player, world);

        var observed = observe(player.observed(), event.evidence());
        var queue = new ArrayDeque<>(affected(player.observed(), event.evidence()));
        var queued = new HashSet<>(queue);
        var runs = new HashMap<>(player.runs());
        var earned = new ArrayList<>(player.earned());
        var consumedCustom = new HashSet<ObjectiveKey>();
        List<QuestPlayerProgress.EarnedReward> newEarned = new ArrayList<>();
        List<QuestTransitionBatch.ObjectiveCompletion> objectiveCompletions = new ArrayList<>();
        List<QuestTransitionBatch.QuestCompletion> completions = new ArrayList<>();
        while (!queue.isEmpty()) {
            var questId = queue.removeFirst();
            queued.remove(questId);
            var quest = snapshot.quests().get(questId);
            if (quest == null || !eligible(quest, player.activeRuns(), runs)) continue;
            long number = player.currentRun(quest.id());
            var key = new QuestPlayerProgress.RunKey(quest.id(), number);
            var original = runs.get(key);
            if (original != null && (original.completed() || original.archived())) continue;
            var counts = new HashMap<ResourceLocation, Long>(original == null ? Map.of() : original.counts());
            boolean changed;
            int rounds = 0;
            do {
                changed = false;
                for (var objective : quest.objectives()) {
                    long previous = counts.getOrDefault(objective.id(), 0L);
                    if (previous >= objective.goal() || !dependenciesMet(quest, objective, counts)) continue;
                    // Inventory samples can go stale between events. Only a fresh possession event proves possession.
                    long next = objective.parameters() instanceof ObjectiveDefinition.Possession && !(event.evidence() instanceof QuestEvent.Possession)
                            ? previous : QuestObjectiveHandler.observe(objective, observed, previous);
                    if (rounds == 0 && event.evidence() instanceof QuestEvent.CustomEvent customEvent
                            && objective.parameters() instanceof ObjectiveDefinition.CustomEvent parameter
                            && parameter.producer().equals(customEvent.producer())
                            && eligible(quest, player.activeRuns(), player.runs())
                            && dependenciesMet(quest, objective, player.runs().containsKey(key) ? player.runs().get(key).counts() : Map.of())
                            && consumedCustom.add(new ObjectiveKey(key, objective.id()))) {
                        next = QuestObjectiveHandler.addBounded(previous, customEvent.amount(), objective.goal());
                    }
                    if (next != previous) {
                        counts.put(objective.id(), next);
                        changed = true;
                        if (next >= objective.goal()) objectiveCompletions.add(new QuestTransitionBatch.ObjectiveCompletion(quest.id(), number, objective.id()));
                    }
                }
                rounds++;
            } while (changed && rounds <= quest.objectives().size());
            boolean completed = quest.objectives().stream().allMatch(objective -> counts.getOrDefault(objective.id(), 0L) >= objective.goal());
            runs.put(key, new QuestPlayerProgress.Run(quest.definitionVersion(), snapshot.hash(), counts, completed,
                    completed ? event.gameTime() : 0, false));
            if (completed) {
                completions.add(new QuestTransitionBatch.QuestCompletion(quest.id(), number));
                for (var reward : quest.rewards()) {
                    var entitlement = new QuestPlayerProgress.EarnedReward(quest.id(), number, reward, snapshot.hash());
                    earned.add(entitlement);
                    newEarned.add(entitlement);
                }
                for (var dependent : dependents.getOrDefault(quest.id(), Set.of())) {
                    if (queued.add(dependent)) queue.addLast(dependent);
                }
            }
        }
        var recent = new ArrayList<>(player.recentOccurrences());
        recent.add(occurrence);
        if (recent.size() > 512) recent.removeFirst();
        boolean progressionChanged = !runs.equals(player.runs()) || !earned.equals(player.earned());
        var replacement = new QuestPlayerProgress(player.playerId(), progressionChanged ? player.revision() + 1 : player.revision(),
                runs, player.activeRuns(), earned, player.tracking(), recent, observed);
        return new QuestTransitionBatch(replacement, world, objectiveCompletions, completions, newEarned);
    }

    /** Explicit repeat action only; publication and observation never create a new run. */
    public QuestPlayerProgress beginRepeat(QuestPlayerProgress player, ResourceLocation questId, long gameTime) {
        var quest = snapshot.quests().get(questId);
        var run = player.run(questId);
        if (quest == null || !quest.repeatable() || run == null || !run.completed() || run.archived()
                || gameTime < run.completedTick() || gameTime - run.completedTick() < quest.cooldownTicks()
                || player.currentRun(questId) == Long.MAX_VALUE) return player;
        long next = player.currentRun(questId) + 1;
        var active = new HashMap<>(player.activeRuns());
        active.put(questId, next);
        var runs = new HashMap<>(player.runs());
        runs.put(new QuestPlayerProgress.RunKey(questId, next),
                new QuestPlayerProgress.Run(quest.definitionVersion(), snapshot.hash(), Map.of(), false, 0, false));
        return new QuestPlayerProgress(player.playerId(), player.revision() + 1, runs, active, player.earned(),
                player.tracking(), player.recentOccurrences(), player.observed());
    }

    private boolean eligible(QuestDefinition quest, Map<ResourceLocation, Long> active, Map<QuestPlayerProgress.RunKey, QuestPlayerProgress.Run> runs) {
        var chapter = snapshot.chapters().get(quest.chapter());
        if (chapter == null || chapter.disabled()) return false;
        if (quest.prerequisites().isEmpty()) return true;
        var completed = quest.prerequisites().stream().map(id -> {
            long number = active.getOrDefault(id, 0L);
            var run = runs.get(new QuestPlayerProgress.RunKey(id, number));
            return number > 0 || run != null && run.completed();
        });
        return quest.prerequisiteMode() == QuestDefinition.PrerequisiteMode.ALL
                ? completed.allMatch(Boolean::booleanValue) : completed.anyMatch(Boolean::booleanValue);
    }

    private static boolean dependenciesMet(QuestDefinition quest, ObjectiveDefinition objective, Map<ResourceLocation, Long> counts) {
        for (var dependency : objective.dependsOn()) {
            var prerequisite = quest.objectives().stream().filter(value -> value.id().equals(dependency)).findFirst().orElse(null);
            if (prerequisite == null || counts.getOrDefault(dependency, 0L) < prerequisite.goal()) return false;
        }
        return true;
    }

    private Set<ResourceLocation> affected(QuestPlayerProgress.Observed previous, QuestEvent.Evidence evidence) {
        Set<ResourceLocation> affected = new LinkedHashSet<>();
        switch (evidence) {
            case QuestEvent.Possession possession -> {
                inventoryTargets(affected, previous.inventory());
                inventoryTargets(affected, possession.inventory());
            }
            case QuestEvent.Dimension dimension -> affected.addAll(dimensions.getOrDefault(dimension.dimension(), Set.of()));
            case QuestEvent.Lore record -> affected.addAll(lore.getOrDefault(record.lore(), Set.of()));
            case QuestEvent.CustomEvent event -> affected.addAll(custom.getOrDefault(event.producer(), Set.of()));
            case QuestEvent.SharedFact ignored -> { }
        }
        return affected;
    }

    private void inventoryTargets(Set<ResourceLocation> affected, ItemObservation inventory) {
        for (var stack : inventory.stacks()) {
            affected.addAll(items.getOrDefault(stack.item(), Set.of()));
            for (var tag : stack.tags()) affected.addAll(tags.getOrDefault(tag, Set.of()));
        }
    }

    private static QuestPlayerProgress.Observed observe(QuestPlayerProgress.Observed previous, QuestEvent.Evidence evidence) {
        return switch (evidence) {
            case QuestEvent.Possession possession -> new QuestPlayerProgress.Observed(possession.inventory(), previous.dimension(), previous.lore());
            case QuestEvent.Dimension dimension -> new QuestPlayerProgress.Observed(previous.inventory(), dimension.dimension(), previous.lore());
            case QuestEvent.Lore record -> {
                var records = new HashSet<>(previous.lore());
                records.add(record.lore());
                yield new QuestPlayerProgress.Observed(previous.inventory(), previous.dimension(), records);
            }
            default -> previous;
        };
    }

    private static void index(Map<ResourceLocation, Set<ResourceLocation>> index, ResourceLocation target, ResourceLocation quest) {
        index.computeIfAbsent(target, ignored -> new LinkedHashSet<>()).add(quest);
    }
    private record ObjectiveKey(QuestPlayerProgress.RunKey run, ResourceLocation objective) { }
}
