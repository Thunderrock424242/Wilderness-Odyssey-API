package com.thunder.wildernessodysseyapi.quest.validation;

import net.minecraft.resources.ResourceLocation;

import java.util.Set;

/** Immutable availability captured on the server thread; reload workers must not query live registries. */
public interface QuestContentLookup {
    enum ContentKind { ITEM, DIMENSION, LORE, MOD }

    /** MOD uses the mod ID as namespace with path {@code loaded}. */
    boolean contains(ContentKind kind, ResourceLocation id);
    Set<ResourceLocation> tagMembers(ResourceLocation tag);
    boolean supportsEvent(ResourceLocation producer);
    long generation();
}
