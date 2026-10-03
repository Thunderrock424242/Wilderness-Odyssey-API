package com.thunder.wildernessodysseyapi.quest.progress;

import com.thunder.wildernessodysseyapi.quest.definition.RewardDefinition;
import com.thunder.wildernessodysseyapi.quest.objective.ItemObservation;
import net.minecraft.resources.ResourceLocation;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/** Immutable individual state. Historical runs and original entitlements survive new publications. */
public record QuestPlayerProgress(UUID playerId, long revision, Map<RunKey, Run> runs,
                                  Map<ResourceLocation, Long> activeRuns, List<EarnedReward> earned,
                                  ResourceLocation tracking, List<Occurrence> recentOccurrences, Observed observed) {
    public QuestPlayerProgress {
        Objects.requireNonNull(playerId);
        runs = Map.copyOf(runs);
        activeRuns = Map.copyOf(activeRuns);
        earned = List.copyOf(earned);
        recentOccurrences = List.copyOf(recentOccurrences);
        Objects.requireNonNull(observed);
    }

    public static QuestPlayerProgress empty(UUID player) {
        return new QuestPlayerProgress(player, 0, Map.of(), Map.of(), List.of(), null, List.of(), Observed.empty());
    }

    public long currentRun(ResourceLocation quest) {
        return activeRuns.getOrDefault(quest, 0L);
    }

    public Run run(ResourceLocation quest) {
        return runs.get(new RunKey(quest, currentRun(quest)));
    }

    public long count(ResourceLocation quest, ResourceLocation objective) {
        var run = run(quest);
        return run == null ? 0 : run.counts().getOrDefault(objective, 0L);
    }

    public boolean completed(ResourceLocation quest) {
        var run = run(quest);
        return run != null && run.completed();
    }

    public boolean completedEver(ResourceLocation quest) {
        // Repeating can start only after a completed run, so a positive active run proves prior completion.
        return currentRun(quest) > 0 || completed(quest);
    }

    public record RunKey(ResourceLocation quest, long number) {
        public RunKey {
            Objects.requireNonNull(quest);
            if (number < 0) throw new IllegalArgumentException("Negative run number.");
        }
    }

    public record Run(int definitionVersion, String definitionHash, Map<ResourceLocation, Long> counts,
                      boolean completed, long completedTick, boolean archived) {
        public Run {
            counts = Map.copyOf(counts);
            if (counts.values().stream().anyMatch(count -> count < 0)) throw new IllegalArgumentException("Negative progress count.");
        }
    }

    public record EarnedReward(ResourceLocation quest, long runNumber, RewardDefinition originalValue, String definitionHash) { }
    public record Occurrence(UUID session, UUID id) { }

    /** Runtime observation is refreshed by server adapters; inventory/dimension are never trusted after login. */
    public record Observed(ItemObservation inventory, ResourceLocation dimension, Set<ResourceLocation> lore) {
        public Observed {
            Objects.requireNonNull(inventory);
            lore = Set.copyOf(lore);
        }

        public static Observed empty() {
            return new Observed(ItemObservation.empty(), null, Set.of());
        }
    }
}
