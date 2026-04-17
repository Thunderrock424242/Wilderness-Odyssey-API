package com.thunder.wildernessodysseyapi.config;

import net.neoforged.neoforge.common.ModConfigSpec;
import org.apache.commons.lang3.tuple.Pair;

/**
 * Common configuration that lets modpack creators declare ownership/credit details.
 */
public final class OwnershipConfig {

    public static final OwnershipConfig CONFIG;
    public static final ModConfigSpec CONFIG_SPEC;

    static {
        Pair<OwnershipConfig, ModConfigSpec> pair = new ModConfigSpec.Builder()
                .configure(OwnershipConfig::new);
        CONFIG = pair.getLeft();
        CONFIG_SPEC = pair.getRight();
    }

    private final ModConfigSpec.ConfigValue<String> ownerName;
    private final ModConfigSpec.ConfigValue<String> projectName;
    private final ModConfigSpec.ConfigValue<String> ownershipNotice;
    private final ModConfigSpec.ConfigValue<String> supportContact;
    private final ModConfigSpec.BooleanValue showNoticeOnStartup;

    private OwnershipConfig(ModConfigSpec.Builder builder) {
        builder.push("ownership");

        ownerName = builder.comment("Primary owner/creator name shown in the ownership notice.")
                .define("ownerName", "thunderrock424242");

        projectName = builder.comment("Project or modpack name to display in the ownership notice.")
                .define("projectName", "Wilderness Odyssey");

        ownershipNotice = builder.comment("Custom statement shown in logs to identify ownership and authorship.")
                .define("ownershipNotice", "This modpack is owned by thunderrock424242. Do not repost without permission.");

        supportContact = builder.comment("Optional support/contact info (Discord, email, website).")
                .define("supportContact", "Add me on discord thunderrock424242");

        showNoticeOnStartup = builder.comment("If true, logs the ownership notice every time the server starts.")
                .define("showNoticeOnStartup", true);

        builder.pop();
    }

    public String ownerName() {
        return ownerName.get();
    }

    public String projectName() {
        return projectName.get();
    }

    public String ownershipNotice() {
        return ownershipNotice.get();
    }

    public String supportContact() {
        return supportContact.get();
    }

    public boolean showNoticeOnStartup() {
        return showNoticeOnStartup.get();
    }
}