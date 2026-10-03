package com.thunder.wildernessodysseyapi.quest.integration;

import com.thunder.wildernessodysseyapi.lorebook.LoreBookManager;
import com.thunder.wildernessodysseyapi.quest.validation.QuestContentLookup;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.fml.ModList;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/** Captures detached registry/tag/lore/producer availability at the reload or server-thread boundary. */
public record QuestAvailability(Set<ResourceLocation> items, Set<ResourceLocation> dimensions, Set<ResourceLocation> lore,
                                Set<String> mods, Map<ResourceLocation, Set<ResourceLocation>> tags,
                                Set<ResourceLocation> producers, long generation) implements QuestContentLookup {
    public QuestAvailability {
        items = Set.copyOf(items); dimensions = Set.copyOf(dimensions); lore = Set.copyOf(lore); mods = Set.copyOf(mods);
        var copy = new HashMap<ResourceLocation, Set<ResourceLocation>>(); tags.forEach((id, members) -> copy.put(id, Set.copyOf(members))); tags = Map.copyOf(copy);
        producers = Set.copyOf(producers);
    }
    public static QuestAvailability capture(RegistryAccess access, long generation) {
        var tags = new HashMap<ResourceLocation, Set<ResourceLocation>>();
        BuiltInRegistries.ITEM.getTags().forEach(pair -> {
            var members = new HashSet<ResourceLocation>();
            pair.getSecond().forEach(holder -> members.add(BuiltInRegistries.ITEM.getKey(holder.value())));
            tags.put(pair.getFirst().location(), members);
        });
        var lore = new HashSet<ResourceLocation>();
        LoreBookManager.config().books().forEach(book -> LoreQuestBridge.map(book.id()).ifPresent(lore::add));
        var mods = new HashSet<String>(); ModList.get().getMods().forEach(mod -> mods.add(mod.getModId()));
        return new QuestAvailability(BuiltInRegistries.ITEM.keySet(), access.registryOrThrow(Registries.LEVEL_STEM).keySet(), lore,
                mods, tags, QuestCustomEventRegistry.producers(), generation);
    }
    @Override public boolean contains(ContentKind kind, ResourceLocation id) {
        return switch (kind) {
            case ITEM -> items.contains(id);
            case DIMENSION -> dimensions.contains(id);
            case LORE -> lore.contains(id);
            case MOD -> mods.contains(id.getNamespace());
        };
    }
    @Override public Set<ResourceLocation> tagMembers(ResourceLocation tag) { return tags.getOrDefault(tag, Set.of()); }
    @Override public boolean supportsEvent(ResourceLocation producer) { return producers.contains(producer); }
}
