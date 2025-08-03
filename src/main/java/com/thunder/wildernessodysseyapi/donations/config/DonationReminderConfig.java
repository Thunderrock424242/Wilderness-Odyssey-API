package com.thunder.wildernessodysseyapi.donations.config;

import com.thunder.wildernessodysseyapi.ModPackPatches.client.WorldVersionChecker;
import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * Configuration options for donation reminders.
 */
public class DonationReminderConfig {
    public static final ModConfigSpec.BooleanValue disableReminder;
    public static final ModConfigSpec.ConfigValue<String> optOutWorldVersion;
    public static final DonationReminderConfig INSTANCE;
    public static final ModConfigSpec CONFIG_SPEC;

    static {
        ModConfigSpec.Builder builder = new ModConfigSpec.Builder();
        disableReminder = builder.comment("Disable donation reminders").define("disableReminder", false);
        optOutWorldVersion = builder.comment("World version when opt out was last set")
                .define("optOutWorldVersion", WorldVersionChecker.MOD_DEFAULT_WORLD_VERSION);
        CONFIG_SPEC = builder.build();
        INSTANCE = new DonationReminderConfig();
    }

    /** Saves the configuration to disk. */
    public static void save() {
        CONFIG_SPEC.save();
    }

    /**
     * Resets opt-out if the stored version differs from the current modpack version.
     */
    public static void validateVersion() {
        String currentVersion = WorldVersionChecker.MOD_DEFAULT_WORLD_VERSION;
        if (!optOutWorldVersion.get().equals(currentVersion)) {
            disableReminder.set(false);
            optOutWorldVersion.set(currentVersion);
            save();
        }
    }
}
