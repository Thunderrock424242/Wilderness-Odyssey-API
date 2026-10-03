package com.thunder.wildernessodysseyapi.quest.definition;

import net.minecraft.resources.ResourceLocation;

import java.util.List;
import java.util.Objects;

/** Optional quest eligibility describes progression; it never gates normal Minecraft mechanics. */
public record QuestDefinition(ResourceLocation id, int definitionVersion, ResourceLocation chapter,
                              String title, String description, ResourceLocation icon,
                              boolean optional, boolean hidden, boolean repeatable, long cooldownTicks,
                              PrerequisiteMode prerequisiteMode, List<ResourceLocation> prerequisites,
                              List<ObjectiveDefinition> objectives, List<RewardDefinition> rewards) {
    public enum PrerequisiteMode { ALL, ANY }

    public QuestDefinition {
        Objects.requireNonNull(id);
        Objects.requireNonNull(chapter);
        Objects.requireNonNull(title);
        Objects.requireNonNull(description);
        Objects.requireNonNull(icon);
        Objects.requireNonNull(prerequisiteMode);
        prerequisites = List.copyOf(prerequisites);
        objectives = List.copyOf(objectives);
        rewards = List.copyOf(rewards);
    }
}
