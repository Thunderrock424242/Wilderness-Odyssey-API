package com.thunder.wildernessodysseyapi.quest.objective;

import net.minecraft.resources.ResourceLocation;

import java.util.Objects;
import java.util.UUID;

/** Verified server evidence. Occurrence identifies a physical transition, not a tick or packet retry. */
public record QuestEvent(UUID serverSession, UUID occurrenceId, UUID actor, long gameTime, Evidence evidence) {
    public QuestEvent {
        Objects.requireNonNull(serverSession);
        Objects.requireNonNull(occurrenceId);
        Objects.requireNonNull(evidence);
        if (gameTime < 0) {
            throw new IllegalArgumentException("Negative game time.");
        }
    }

    public sealed interface Evidence permits Possession, Dimension, Lore, CustomEvent, SharedFact { }
    public record Possession(ItemObservation inventory) implements Evidence {
        public Possession { Objects.requireNonNull(inventory); }
    }
    public record Dimension(ResourceLocation dimension) implements Evidence {
        public Dimension { Objects.requireNonNull(dimension); }
    }
    public record Lore(ResourceLocation lore) implements Evidence {
        public Lore { Objects.requireNonNull(lore); }
    }
    public record CustomEvent(ResourceLocation producer, long amount) implements Evidence {
        public CustomEvent {
            Objects.requireNonNull(producer);
            if (amount <= 0) throw new IllegalArgumentException("Custom event amount must be positive.");
        }
    }
    public record SharedFact(ResourceLocation fact) implements Evidence {
        public SharedFact { Objects.requireNonNull(fact); }
    }
}
