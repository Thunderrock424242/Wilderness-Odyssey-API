package com.thunder.wildernessodysseyapi.quest.runtime;

import com.thunder.wildernessodysseyapi.quest.progress.QuestPlayerProgress;
import com.thunder.wildernessodysseyapi.quest.world.QuestWorldState;
import net.minecraft.resources.ResourceLocation;

import java.util.List;

/** Pure result; adapters own saving, durable entitlements and dirty client synchronization. */
public record QuestTransitionBatch(QuestPlayerProgress player, QuestWorldState world,
                                    List<ObjectiveCompletion> objectiveCompletions,
                                    List<QuestCompletion> questCompletions,
                                    List<QuestPlayerProgress.EarnedReward> earned) {
    public QuestTransitionBatch {
        objectiveCompletions = List.copyOf(objectiveCompletions);
        questCompletions = List.copyOf(questCompletions);
        earned = List.copyOf(earned);
    }

    public record ObjectiveCompletion(ResourceLocation quest, long run, ResourceLocation objective) { }
    public record QuestCompletion(ResourceLocation quest, long run) { }

    public static QuestTransitionBatch unchanged(QuestPlayerProgress player, QuestWorldState world) {
        return new QuestTransitionBatch(player, world, List.of(), List.of(), List.of());
    }
}
