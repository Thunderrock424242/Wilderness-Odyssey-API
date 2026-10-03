package com.thunder.wildernessodysseyapi.quest.definition;

import com.google.gson.JsonObject;
import net.minecraft.resources.ResourceLocation;

import java.util.Objects;

/** Bounded source input; identity includes kind so chapters and quests cannot overwrite each other. */
public record QuestSourceDocument(Kind kind, ResourceLocation id, JsonObject content, String sourcePack) {
    public enum Kind { CAMPAIGN, CHAPTER, QUEST }

    public QuestSourceDocument {
        Objects.requireNonNull(kind);
        Objects.requireNonNull(id);
        content = Objects.requireNonNull(content).deepCopy();
        Objects.requireNonNull(sourcePack);
    }

    @Override
    public JsonObject content() {
        return content.deepCopy();
    }
}
