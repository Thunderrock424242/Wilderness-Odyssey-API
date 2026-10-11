package com.thunder.wildernessodysseyapi.temporalrift.hybrid;

import com.thunder.wildernessodysseyapi.config.WildernessConfigSpecs;
import net.neoforged.neoforge.common.ModConfigSpec;

/** Hybrid rift budgets. Server gameplay settings and local accessibility settings have separate owners. */
public final class RiftConfig {
    public static ModConfigSpec.BooleanValue HYBRID_SCHEDULED_RIFTS;
    public static ModConfigSpec.IntValue FORMATION_DIAMETER;
    public static ModConfigSpec.IntValue ROCK_COUNT;
    public static ModConfigSpec.IntValue ROCK_SPACING;
    public static ModConfigSpec.DoubleValue FIELD_RADIUS;
    public static ModConfigSpec.DoubleValue PLAYER_FORCE;
    public static ModConfigSpec.DoubleValue ITEM_FORCE;
    public static ModConfigSpec.DoubleValue BASE_DAMAGE;
    public static ModConfigSpec.DoubleValue VELOCITY_DAMAGE;
    public static ModConfigSpec.DoubleValue MAX_DAMAGE;
    public static ModConfigSpec.BooleanValue ENTRY_STATUS;
    public static ModConfigSpec.BooleanValue LOCAL_FX;
    public static ModConfigSpec.BooleanValue POST_PROCESSING;
    public static ModConfigSpec.BooleanValue CAMERA_MOTION;
    public static ModConfigSpec.DoubleValue FX_STRENGTH;
    public static ModConfigSpec.DoubleValue PARTICLE_DENSITY;

    static { WildernessConfigSpecs.initialize(); }
    private RiftConfig() { }

    public static void defineServer(ModConfigSpec.Builder builder) {
        builder.push("anomaly_rift");
        HYBRID_SCHEDULED_RIFTS = builder.comment("New scheduled and /wildernessrift open rifts use the hybrid ring. Existing saved temporal cores retain their travel behavior.")
                .define("hybridScheduledRifts", true);
        FORMATION_DIAMETER = builder.comment("Maximum composition width in blocks, including rocks.")
                .defineInRange("formationDiameter", 24, 15, 30);
        ROCK_COUNT = builder.comment("Maximum rock templates per composition; constrained sites may fit fewer.")
                .defineInRange("rockCount", 8, 1, 12);
        ROCK_SPACING = builder.comment("Minimum spacing between template bounding volumes, in blocks.")
                .defineInRange("rockSpacing", 1, 0, 4);
        FIELD_RADIUS = builder.comment("Local presentation radius in blocks. Physics operates only within 10 blocks.")
                .defineInRange("fieldRadius", 30.0, 10.0, 48.0);
        PLAYER_FORCE = builder.comment("Maximum player acceleration in blocks/tick per application (every 5 ticks); zero disables. Creative/spectator players are excluded.")
                .defineInRange("playerForce", 0.018, 0.0, 0.04);
        ITEM_FORCE = builder.comment("Dropped item acceleration every 5 ticks, in blocks/tick. Zero disables.")
                .defineInRange("itemForce", 0.035, 0.0, 0.08);
        BASE_DAMAGE = builder.comment("Unstable entry damage in health points (2 points = 1 heart). Capped against current health to leave at least 1 point before external damage modifiers.")
                .defineInRange("baseDamage", 4.0, 0.0, 12.0);
        VELOCITY_DAMAGE = builder.comment("Extra health points per block/tick of entry speed.")
                .defineInRange("velocityDamage", 2.0, 0.0, 8.0);
        MAX_DAMAGE = builder.comment("Cap on entry damage before armor/effect mitigation, in health points.")
                .defineInRange("maximumDamage", 8.0, 0.0, 16.0);
        ENTRY_STATUS = builder.comment("Apply a brief Slowness effect after unstable entry.")
                .define("entryStatus", true);
        builder.pop();
    }

    public static void defineClient(ModConfigSpec.Builder builder) {
        builder.push("anomaly_rift");
        LOCAL_FX = builder.comment("Enable local rift ambience, particles, fog and screen effects. Ring and aperture remain visible.")
                .define("localFx", true);
        POST_PROCESSING = builder.comment("Optional pulse lens warp, color separation and vignette. Disabled under active/unknown external shader packs.")
                .define("postProcessing", true);
        CAMERA_MOTION = builder.comment("Allow small camera roll/FOV pulses. Disable for motion sensitivity.")
                .define("cameraMotion", false);
        FX_STRENGTH = builder.comment("Local visual intensity from 0 to 1. Zero disables intense effects.")
                .defineInRange("fxStrength", 0.5, 0.0, 1.0);
        PARTICLE_DENSITY = builder.comment("Particle density multiplier from 0 to 1; Minecraft particle settings also apply.")
                .defineInRange("particleDensity", 0.6, 0.0, 1.0);
        builder.pop();
    }
}
