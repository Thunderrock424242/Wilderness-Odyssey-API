package com.thunder.wildernessodysseyapi.quest;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.thunder.wildernessodysseyapi.quest.definition.QuestSourceDocument;
import com.thunder.wildernessodysseyapi.quest.validation.QuestContentLookup;
import net.minecraft.resources.ResourceLocation;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/** Small hand-authored campaign with all supported foundation providers. */
public final class QuestFixtures {
    private QuestFixtures() {
    }

    public static ResourceLocation id(String id) {
        return ResourceLocation.parse(id);
    }

    public static List<QuestSourceDocument> documents() {
        return new ArrayList<>(List.of(
                source(QuestSourceDocument.Kind.CAMPAIGN, "test:campaign", "campaign"),
                source(QuestSourceDocument.Kind.CHAPTER, "test:start", "chapter"),
                source(QuestSourceDocument.Kind.QUEST, "test:first", "quest")));
    }

    public static QuestSourceDocument source(QuestSourceDocument.Kind kind, String id, String fixture) {
        var stream = QuestFixtures.class.getResourceAsStream("/quests/valid_campaign/" + fixture + ".json");
        if (stream == null) {
            throw new IllegalStateException("Missing quest fixture " + fixture);
        }
        try (var reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
            return new QuestSourceDocument(kind, id(id), JsonParser.parseReader(reader).getAsJsonObject(), "fixture");
        } catch (java.io.IOException exception) {
            throw new java.io.UncheckedIOException(exception);
        }
    }

    public static QuestSourceDocument replace(QuestSourceDocument source, JsonObject json) {
        return new QuestSourceDocument(source.kind(), source.id(), json, source.sourcePack());
    }

    public static QuestContentLookup lookup() {
        return lookup(7);
    }

    public static QuestContentLookup lookup(long generation) {
        return new QuestContentLookup() {
            @Override
            public boolean contains(ContentKind kind, ResourceLocation id) {
                return !id.getPath().startsWith("missing") && !id.getNamespace().startsWith("missing");
            }

            @Override
            public Set<ResourceLocation> tagMembers(ResourceLocation tag) {
                return tag.getPath().startsWith("missing") ? Set.of() : Set.of(id("minecraft:oak_log"));
            }

            @Override
            public boolean supportsEvent(ResourceLocation producer) {
                return producer.equals(id("test:beacon"));
            }

            @Override
            public long generation() {
                return generation;
            }
        };
    }
}
