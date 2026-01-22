package com.thunder.wildernessodysseyapi.telemetry;

import com.thunder.wildernessodysseyapi.Core.ModConstants;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * Persists player telemetry consent decisions on the server.
 */
public class TelemetryConsentStore extends SavedData {
    private static final String DATA_NAME = ModConstants.MOD_ID + "_telemetry_consent";
    private static final String VERSION_KEY = "version";
    private static final String DECISIONS_KEY = "decisions";

    private final Map<UUID, ConsentDecision> decisions = new HashMap<>();
    private String version = ModConstants.VERSION;

    public TelemetryConsentStore() {
    }

    public TelemetryConsentStore(CompoundTag tag, HolderLookup.Provider registries) {
        String savedVersion = tag.contains(VERSION_KEY, Tag.TAG_STRING) ? tag.getString(VERSION_KEY) : "";
        if (!ModConstants.VERSION.equals(savedVersion)) {
            this.version = ModConstants.VERSION;
            return;
        }
        this.version = savedVersion;
        CompoundTag decisionTag = tag.getCompound(DECISIONS_KEY);
        for (String key : decisionTag.getAllKeys()) {
            try {
                UUID uuid = UUID.fromString(key);
                ConsentDecision decision = ConsentDecision.fromString(decisionTag.getString(key));
                this.decisions.put(uuid, decision);
            } catch (IllegalArgumentException ignored) {
                // Skip invalid UUID entries.
            }
        }
    }

    public static TelemetryConsentStore get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(
                new SavedData.Factory<>(TelemetryConsentStore::new, TelemetryConsentStore::new),
                DATA_NAME
        );
    }

    public ConsentDecision getDecision(UUID uuid) {
        if (uuid == null) {
            return ConsentDecision.ACCEPTED;
        }
        return decisions.getOrDefault(uuid, ConsentDecision.ACCEPTED);
    }

    public void setDecision(UUID uuid, ConsentDecision decision) {
        if (uuid == null || decision == null) {
            return;
        }
        decisions.put(uuid, decision);
        setDirty();
    }

    @Override
    public @NotNull CompoundTag save(@NotNull CompoundTag tag, HolderLookup.@NotNull Provider registries) {
        tag.putString(VERSION_KEY, version);
        CompoundTag decisionTag = new CompoundTag();
        decisions.forEach((uuid, decision) -> decisionTag.putString(uuid.toString(), decision.serialized()));
        tag.put(DECISIONS_KEY, decisionTag);
        return tag;
    }

    public enum ConsentDecision {
        ACCEPTED("accepted"),
        DECLINED("declined"),
        UNKNOWN("unknown");

        private final String serialized;

        ConsentDecision(String serialized) {
            this.serialized = serialized;
        }

        public String serialized() {
            return serialized;
        }

        public static ConsentDecision fromString(String value) {
            if (value == null) {
                return UNKNOWN;
            }
            String normalized = value.trim().toLowerCase(Locale.ROOT);
            for (ConsentDecision decision : values()) {
                if (decision.serialized.equals(normalized)) {
                    return decision;
                }
            }
            return UNKNOWN;
        }
    }
}
