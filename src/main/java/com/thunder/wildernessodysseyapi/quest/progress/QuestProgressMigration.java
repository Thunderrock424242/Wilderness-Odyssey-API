package com.thunder.wildernessodysseyapi.quest.progress;

import com.thunder.wildernessodysseyapi.quest.definition.ObjectiveDefinition;
import com.thunder.wildernessodysseyapi.quest.definition.QuestCampaignSnapshot;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/** Non-destructive compatibility and explicit mappings. Never delivers rewards or rewrites claim identities. */
public final class QuestProgressMigration {
    public enum Kind { QUEST_REMOVED, OBJECTIVE_REMOVED, OBJECTIVE_MEANING, ELIGIBILITY, REPEAT_POLICY }
    public record ObjectiveKey(ResourceLocation quest, ResourceLocation objective) { }
    public record Change(ResourceLocation quest, ResourceLocation objective, Kind kind) { }

    public MigrationReport check(QuestCampaignSnapshot previous, QuestCampaignSnapshot candidate) {
        List<Change> changes = new ArrayList<>();
        for (var old : previous.quests().values()) {
            var next = candidate.quests().get(old.id());
            if (next == null) {
                changes.add(new Change(old.id(), null, Kind.QUEST_REMOVED));
                continue;
            }
            if (old.prerequisiteMode() != next.prerequisiteMode() || !new HashSet<>(old.prerequisites()).equals(new HashSet<>(next.prerequisites()))) {
                changes.add(new Change(old.id(), null, Kind.ELIGIBILITY));
            }
            if (old.repeatable() != next.repeatable() || old.cooldownTicks() != next.cooldownTicks()) {
                changes.add(new Change(old.id(), null, Kind.REPEAT_POLICY));
            }
            for (var objective : old.objectives()) {
                var replacement = next.objectives().stream().filter(value -> value.id().equals(objective.id())).findFirst().orElse(null);
                if (replacement == null) {
                    changes.add(new Change(old.id(), objective.id(), Kind.OBJECTIVE_REMOVED));
                } else if (!objective.parameters().equals(replacement.parameters()) || objective.goal() != replacement.goal()
                        || !new HashSet<>(objective.dependsOn()).equals(new HashSet<>(replacement.dependsOn()))) {
                    changes.add(new Change(old.id(), objective.id(), Kind.OBJECTIVE_MEANING));
                }
            }
        }
        return new MigrationReport(previous, candidate, changes);
    }

    public MigrationResult apply(QuestPlayerProgress progress, MigrationPlan plan) {
        var report = check(plan.previous(), plan.candidate());
        if (!new HashSet<>(plan.approvedChanges()).containsAll(report.changes())) {
            return new MigrationResult(Optional.empty(), "The migration needs an explicit decision for each incompatible change.");
        }
        for (var change : report.changes()) {
            if (change.kind() == Kind.OBJECTIVE_MEANING && !plan.objectiveMappings().containsKey(new ObjectiveKey(change.quest(), change.objective()))) {
                return new MigrationResult(Optional.empty(), "Changed objective meaning requires an explicit objective mapping.");
            }
        }
        Map<QuestPlayerProgress.RunKey, QuestPlayerProgress.Run> runs = new HashMap<>(progress.runs());
        var active = new HashMap<>(progress.activeRuns());
        for (var entry : progress.runs().entrySet()) {
            var key = entry.getKey();
            var value = entry.getValue();
            var targetQuest = plan.questMappings().getOrDefault(key.quest(), key.quest());
            boolean removed = !plan.candidate().quests().containsKey(key.quest());
            if (removed || !targetQuest.equals(key.quest())) {
                runs.put(key, new QuestPlayerProgress.Run(value.definitionVersion(), value.definitionHash(), value.counts(),
                        value.completed(), value.completedTick(), true));
            }
            var counts = new HashMap<>(value.counts());
            for (var mapping : plan.objectiveMappings().entrySet()) {
                if (!mapping.getKey().quest().equals(key.quest())) continue;
                if (!mapping.getValue().quest().equals(targetQuest)) return new MigrationResult(Optional.empty(), "An objective mapping has a different destination quest.");
                var definition = plan.candidate().quests().get(targetQuest);
                if (definition == null || definition.objectives().stream().noneMatch(objective -> objective.id().equals(mapping.getValue().objective()))) {
                    return new MigrationResult(Optional.empty(), "An objective mapping destination is unavailable.");
                }
                long count = value.counts().getOrDefault(mapping.getKey().objective(), 0L);
                if (!mapping.getKey().objective().equals(mapping.getValue().objective()) && counts.containsKey(mapping.getValue().objective())) {
                    return new MigrationResult(Optional.empty(), "An objective mapping would overwrite existing progress.");
                }
                // Keep old keys as archived evidence; the destination is explicit and never adds counts twice.
                counts.put(mapping.getValue().objective(), count);
            }
            if (!targetQuest.equals(key.quest())) {
                if (!plan.candidate().quests().containsKey(targetQuest)) return new MigrationResult(Optional.empty(), "A quest mapping destination is unavailable.");
                var targetKey = new QuestPlayerProgress.RunKey(targetQuest, key.number());
                if (runs.containsKey(targetKey)) return new MigrationResult(Optional.empty(), "A quest mapping would overwrite existing progress.");
                runs.put(targetKey, new QuestPlayerProgress.Run(value.definitionVersion(), value.definitionHash(), counts,
                        value.completed(), value.completedTick(), false));
                active.put(targetQuest, progress.currentRun(key.quest()));
            } else if (!removed && !counts.equals(value.counts())) {
                runs.put(key, new QuestPlayerProgress.Run(value.definitionVersion(), value.definitionHash(), counts,
                        value.completed(), value.completedTick(), value.archived()));
            }
        }
        var tracking = progress.tracking();
        if (tracking != null && !plan.candidate().quests().containsKey(tracking)) tracking = null;
        boolean changed = !runs.equals(progress.runs()) || !active.equals(progress.activeRuns()) || !java.util.Objects.equals(tracking, progress.tracking());
        var result = new QuestPlayerProgress(progress.playerId(), changed ? progress.revision() + 1 : progress.revision(), runs, active,
                progress.earned(), tracking, progress.recentOccurrences(), progress.observed());
        return new MigrationResult(Optional.of(result), "Progress and original entitlements preserved.");
    }

    public record MigrationReport(QuestCampaignSnapshot previous, QuestCampaignSnapshot candidate, List<Change> changes) {
        public MigrationReport { changes = List.copyOf(changes); }
        public boolean compatible() { return changes.isEmpty(); }
        public MigrationPlan preservePlan() {
            if (!compatible()) throw new IllegalStateException("Incompatible definition changes require an explicit migration plan.");
            return new MigrationPlan(previous, candidate, Map.of(), Map.of(), List.of());
        }
    }

    public record MigrationPlan(QuestCampaignSnapshot previous, QuestCampaignSnapshot candidate,
                                Map<ResourceLocation, ResourceLocation> questMappings,
                                Map<ObjectiveKey, ObjectiveKey> objectiveMappings, List<Change> approvedChanges) {
        public MigrationPlan {
            questMappings = Map.copyOf(questMappings);
            objectiveMappings = Map.copyOf(objectiveMappings);
            approvedChanges = List.copyOf(approvedChanges);
        }
    }

    public record MigrationResult(Optional<QuestPlayerProgress> progress, String message) {
        public boolean accepted() { return progress.isPresent(); }
    }
}
