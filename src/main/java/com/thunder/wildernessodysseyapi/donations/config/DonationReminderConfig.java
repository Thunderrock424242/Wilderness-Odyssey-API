package com.thunder.wildernessodysseyapi.donations.config;

import com.thunder.wildernessodysseyapi.core.ModConstants;
import com.thunder.wildernessodysseyapi.config.WildernessConfigSpecs;
import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * Configuration options for donation reminders.
 */
public class DonationReminderConfig {
    public static ModConfigSpec.BooleanValue disableReminder;
    public static ModConfigSpec.ConfigValue<String> optOutReleaseVersion;
    /** @deprecated use {@link #optOutReleaseVersion}; retained for source compatibility. */
    @Deprecated(forRemoval = false)
    public static ModConfigSpec.ConfigValue<String> optOutWorldVersion;
    public static final DonationReminderConfig INSTANCE = new DonationReminderConfig();
    public static ModConfigSpec CONFIG_SPEC;

    static {
        WildernessConfigSpecs.initialize();
    }

    /** Defines the donations category in the unified client config. */
    public static void define(ModConfigSpec.Builder builder) {
        disableReminder = builder.comment("Disable donation reminders").define("disableReminder", false);
        optOutReleaseVersion = builder.comment("Packaged mod release when opt out was last set")
                .define("optOutReleaseVersion", ModConstants.VERSION);
        optOutWorldVersion = optOutReleaseVersion;
    }

    /** Saves the configuration to disk. */
    public static void save() {
        CONFIG_SPEC.save();
    }

    /**
     * Resets opt-out if the stored version differs from the current modpack version.
     */
    public static void validateVersion() {
        if (!CONFIG_SPEC.isLoaded()) return;

        String currentVersion = ModConstants.currentVersion();
        if (!optOutReleaseVersion.get().equals(currentVersion)) {
            disableReminder.set(false);
            optOutReleaseVersion.set(currentVersion);
            save();
        }
    }
}
