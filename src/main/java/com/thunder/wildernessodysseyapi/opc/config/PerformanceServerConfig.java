package com.thunder.wildernessodysseyapi.opc.config;

import com.thunder.wildernessodysseyapi.config.WildernessConfigSpecs;

import com.thunder.wildernessodysseyapi.opc.dataengine.config.DataEngineConfig;
import com.thunder.wildernessodysseyapi.opc.background.config.BackgroundEfficiencyConfig;
import com.thunder.wildernessodysseyapi.opc.tickengine.config.TickEngineConfig;
import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * Unified server configuration for the Wilderness performance stack.
 *
 * <p>Feature engines retain their state owners and compatibility value snapshots;
 * OPC can share optional-work admission. This class assembles their entries in
 * one SERVER file without changing Minecraft or NeoForge lifecycle ownership.</p>
 */
public final class PerformanceServerConfig {
    public static final String FILE_NAME = "wildernessodysseyapi-performance-server.toml";
    public static ModConfigSpec CONFIG_SPEC;

    private static ModConfigSpec.BooleanValue ENABLED;

    static {
        WildernessConfigSpecs.initialize();
    }

    /** Defines the performance category in the unified server config. */
    public static void define(ModConfigSpec.Builder builder) {
        builder.push("performance");
        ENABLED = builder.comment(
                        "Master switch for Wilderness-owned Background, Tick, and Data Engine work.",
                        "Minecraft and NeoForge ticking, chunks, entities, and networking remain authoritative."
                )
                .define("enabled", true);
        com.thunder.wildernessodysseyapi.opc.diagnostics.PerformanceDiagnosticsConfig.define(builder);
        com.thunder.wildernessodysseyapi.opc.core.OpcConfig.define(builder);
        BackgroundEfficiencyConfig.define(builder);
        TickEngineConfig.define(builder);
        DataEngineConfig.define(builder);
        builder.pop();
    }

    private PerformanceServerConfig() {
    }

    /** Triggers definition assembly before a section reads its values. */
    public static void initialize() {
        WildernessConfigSpecs.initialize();
    }

    /** Attaches the unified server spec to this compatibility surface. */
    public static void attachSpec(ModConfigSpec spec) {
        CONFIG_SPEC = spec;
        BackgroundEfficiencyConfig.attachSpec(spec);
        TickEngineConfig.attachSpec(spec);
        DataEngineConfig.attachSpec(spec);
    }

    /** Returns the loaded master switch or its safe default during early startup. */
    public static boolean enabled() {
        try {
            return ENABLED.get();
        } catch (RuntimeException exception) {
            return ENABLED.getDefault();
        }
    }
}
