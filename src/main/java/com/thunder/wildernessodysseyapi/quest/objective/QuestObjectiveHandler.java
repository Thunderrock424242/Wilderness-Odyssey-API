package com.thunder.wildernessodysseyapi.quest.objective;

import com.thunder.wildernessodysseyapi.quest.definition.ObjectiveDefinition;
import com.thunder.wildernessodysseyapi.quest.progress.QuestPlayerProgress;

/** Pure provider evaluation. A full possession result latches; partial possession reflects current supply. */
public final class QuestObjectiveHandler {
    private QuestObjectiveHandler() { }

    public static long observe(ObjectiveDefinition objective, QuestPlayerProgress.Observed observed, long previous) {
        if (previous >= objective.goal()) return objective.goal();
        return switch (objective.parameters()) {
            case ObjectiveDefinition.Possession possession -> {
                long count = 0;
                for (var stack : observed.inventory().stacks()) {
                    if (possession.items().contains(stack.item()) || stack.tags().stream().anyMatch(possession.tags()::contains)) {
                        count = addBounded(count, stack.count(), objective.goal());
                    }
                }
                yield count;
            }
            case ObjectiveDefinition.Dimension dimension -> dimension.dimension().equals(observed.dimension()) ? 1 : previous;
            case ObjectiveDefinition.Lore lore -> observed.lore().contains(lore.lore()) ? 1 : previous;
            case ObjectiveDefinition.CustomEvent ignored -> previous;
        };
    }

    public static long addBounded(long previous, long amount, long goal) {
        return amount >= goal - previous ? goal : previous + amount;
    }
}
