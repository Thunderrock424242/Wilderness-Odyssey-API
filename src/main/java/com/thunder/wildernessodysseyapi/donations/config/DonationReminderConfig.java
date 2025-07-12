package com.thunder.wildernessodysseyapi.donations.config;

import net.neoforged.neoforge.common.ModConfigSpec;

public class DonationReminderConfig {
    public static final ModConfigSpec.BooleanValue disableReminder;
    public static final DonationReminderConfig INSTANCE;
    private static final ModConfigSpec CONFIG_SPEC;

    static {
        ModConfigSpec.Builder builder = new ModConfigSpec.Builder();
        disableReminder = builder.comment("Disable donation reminders").define("disableReminder", false);
        CONFIG_SPEC = builder.build();
        INSTANCE = new DonationReminderConfig();
    }

    public void save() {
        CONFIG_SPEC.save();
    }
}
