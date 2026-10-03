package com.thunder.wildernessodysseyapi.quest.integration;

import com.thunder.wildernessodysseyapi.quest.objective.QuestEvent;
import com.thunder.wildernessodysseyapi.quest.runtime.QuestServerEvents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Trusted Java producer seam. No chat, command or serverbound payload can register or emit evidence. */
public final class QuestCustomEventRegistry {
    public record EventSchema(long maximumAmount) {
        public EventSchema { if (maximumAmount < 1 || maximumAmount > 1_000_000) throw new IllegalArgumentException("Invalid custom event schema."); }
    }
    public record VerifiedCustomEvent(ResourceLocation producer, UUID serverSession, UUID occurrenceId, long amount) { }
    private static final Map<ResourceLocation, EventSchema> SCHEMAS = new HashMap<>();
    private QuestCustomEventRegistry() { }
    public static synchronized void register(ResourceLocation producer, EventSchema schema) {
        if (SCHEMAS.size() >= 256 || SCHEMAS.putIfAbsent(producer, schema) != null) throw new IllegalArgumentException("Quest producer is already registered or the schema budget is full.");
    }
    public static synchronized Set<ResourceLocation> producers() { return Set.copyOf(SCHEMAS.keySet()); }
    public static synchronized boolean valid(ResourceLocation producer, long amount) {
        var schema = SCHEMAS.get(producer); return schema != null && amount > 0 && amount <= schema.maximumAmount();
    }
    public static void emit(ServerPlayer actor, VerifiedCustomEvent event) {
        if (actor.getServer() == null || !actor.getServer().isSameThread()) throw new IllegalStateException("Quest producer requires the server thread.");
        if (!com.thunder.wildernessodysseyapi.quest.config.QuestConfig.values().enabled() || !valid(event.producer(), event.amount()) || event.occurrenceId() == null) return;
        QuestServerEvents.session(actor.getServer()).ifPresent(session -> {
            if (session.id().equals(event.serverSession())) session.observe(actor, event.occurrenceId(), new QuestEvent.CustomEvent(event.producer(), event.amount()));
        });
    }
}
