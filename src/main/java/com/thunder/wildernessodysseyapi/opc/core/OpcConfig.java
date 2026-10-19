package com.thunder.wildernessodysseyapi.opc.core;

import com.thunder.wildernessodysseyapi.opc.config.PerformanceServerConfig;
import net.neoforged.neoforge.common.ModConfigSpec;

/** SERVER-owned OPC rollout policy. Legacy tuning keys remain the single source of numeric limits. */
public final class OpcConfig {
    public enum Mode { LEGACY, OFF, OBSERVE, ADAPTIVE }
    private static ModConfigSpec.EnumValue<Mode> mode;

    private OpcConfig() { }

    public static void define(ModConfigSpec.Builder builder) {
        builder.push("opc");
        mode = builder.comment("Odyssey Performance Core rollout mode; restart the world/server to apply.",
                        "LEGACY preserves performance.coordinator.enabled and existing engine policies (default).",
                        "OFF disables OPC observation/control. OBSERVE collects diagnostics without throttling.",
                        "ADAPTIVE opts into shared Data/Tick/Background optional-work admission and recovery.",
                        "Tuning, worker admission, recording and experimental native saving retain their existing",
                        "performance.coordinator keys; performance.safeMode remains enabled by default.")
                .defineEnum("mode", Mode.LEGACY);
        builder.pop();
    }

    public static Mode mode() {
        PerformanceServerConfig.initialize();
        try { return mode.get(); }
        catch (IllegalStateException unloaded) { return mode.getDefault(); }
    }

    public static boolean observes(boolean legacyEnabled) {
        return switch (mode()) {
            case LEGACY -> legacyEnabled;
            case OFF -> false;
            case OBSERVE, ADAPTIVE -> true;
        };
    }
}
