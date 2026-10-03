package com.thunder.wildernessodysseyapi.quest.definition;

import net.minecraft.resources.ResourceLocation;

import java.util.List;
import java.util.Map;

/** Immutable candidate. Its content hash excludes source-pack labels and runtime registry generation. */
public record QuestCampaignSnapshot(int schemaVersion, Campaign campaign,
                                    Map<ResourceLocation, Chapter> chapters,
                                    Map<ResourceLocation, QuestDefinition> quests,
                                    List<QuestSourceDocument> sources, String hash, long registryGeneration) {
    public QuestCampaignSnapshot {
        chapters = Map.copyOf(chapters);
        quests = Map.copyOf(quests);
        sources = List.copyOf(sources);
    }

    public record Campaign(ResourceLocation id, int definitionVersion, String title, String description,
                           List<ResourceLocation> chapters) {
        public Campaign {
            chapters = List.copyOf(chapters);
        }
    }

    public record Chapter(ResourceLocation id, int definitionVersion, ResourceLocation campaign, String title,
                          List<ResourceLocation> quests, List<String> requiredMods, List<String> optionalMods,
                          boolean disabled) {
        public Chapter {
            quests = List.copyOf(quests);
            requiredMods = List.copyOf(requiredMods);
            optionalMods = List.copyOf(optionalMods);
        }
    }
}
