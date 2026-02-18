package com.thunder.wildernessodysseyapi.ModPackPatches.telemetry;

import com.thunder.wildernessodysseyapi.core.ModConstants;
import com.thunder.wildernessodysseyapi.ModPackPatches.telemetry.TelemetryConsentStore.ConsentDecision;
import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * Client-side configuration for telemetry consent UI state.
 */
public final class TelemetryConsentConfig {
    public static final ModConfigSpec CONFIG_SPEC;

    private static final ModConfigSpec.ConfigValue<String> CONSENT_DECISION;
    private static final ModConfigSpec.ConfigValue<String> CONSENT_VERSION;

    static {
        ModConfigSpec.Builder builder = new ModConfigSpec.Builder();
        builder.push("telemetryConsent");

        CONSENT_DECISION = builder.comment("Client-side telemetry consent decision (accepted/declined/unknown).")
                .define("decision", ConsentDecision.UNKNOWN.serialized());
        CONSENT_VERSION = builder.comment("Modpack world version when consent was last recorded.")
                .define("version", ModConstants.MOD_DEFAULT_WORLD_VERSION);

        builder.pop();

        CONFIG_SPEC = builder.build();
    }

    private TelemetryConsentConfig() {
    }

    public static ConsentDecision decision() {
        return ConsentDecision.fromString(CONSENT_DECISION.get());
    }

    public static void setDecision(ConsentDecision decision) {
        CONSENT_DECISION.set(decision.serialized());
        CONSENT_VERSION.set(ModConstants.MOD_DEFAULT_WORLD_VERSION);
        CONFIG_SPEC.save();
    }

    public static void validateVersion() {
        if (!CONFIG_SPEC.isLoaded()) {
            return;
        }
        String currentVersion = ModConstants.MOD_DEFAULT_WORLD_VERSION;
        if (!CONSENT_VERSION.get().equals(currentVersion)) {
            CONSENT_DECISION.set(ConsentDecision.UNKNOWN.serialized());
            CONSENT_VERSION.set(currentVersion);
            CONFIG_SPEC.save();
        }
    }
}
