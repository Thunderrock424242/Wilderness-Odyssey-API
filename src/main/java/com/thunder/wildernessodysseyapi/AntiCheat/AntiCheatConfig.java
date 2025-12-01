package com.thunder.wildernessodysseyapi.AntiCheat;

import net.neoforged.neoforge.common.ModConfigSpec;
import org.apache.commons.lang3.tuple.Pair;

import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Server-side configuration for the anti-cheat system.
 */
public class AntiCheatConfig {

    public static final AntiCheatConfig CONFIG;
    public static final ModConfigSpec CONFIG_SPEC;

    static {
        Pair<AntiCheatConfig, ModConfigSpec> pair = new ModConfigSpec.Builder()
                .configure(AntiCheatConfig::new);
        CONFIG = pair.getLeft();
        CONFIG_SPEC = pair.getRight();
    }

    private final ModConfigSpec.BooleanValue enableKicks;
    private final ModConfigSpec.BooleanValue logDetections;
    private final ModConfigSpec.ConfigValue<List<? extends String>> blacklistedMods;
    private final ModConfigSpec.ConfigValue<List<? extends String>> blacklistedResourcePacks;
    private final ModConfigSpec.ConfigValue<List<? extends String>> blacklistedItems;

    AntiCheatConfig(ModConfigSpec.Builder builder) {
        builder.push("anti_cheat");

        enableKicks = builder.comment("If true, players are kicked when a blacklist rule is triggered. Otherwise they are warned.")
                .translation("wildernessodysseyapi.anti_cheat.enable_kicks")
                .define("enableKicks", true);

        logDetections = builder.comment("Log detections to the server console for auditing.")
                .translation("wildernessodysseyapi.anti_cheat.log_detections")
                .define("logDetections", true);

        blacklistedMods = builder.comment("List of mod IDs that are not allowed on the server.")
                .translation("wildernessodysseyapi.anti_cheat.blacklisted_mods")
                .defineListAllowEmpty(Collections.singletonList("blacklistedMods"),
                        () -> List.of("xray", "cheatutils"),
                        entry -> entry instanceof String && !((String) entry).isBlank());

        blacklistedResourcePacks = builder.comment("List of client resource pack identifiers that are blocked.")
                .translation("wildernessodysseyapi.anti_cheat.blacklisted_resource_packs")
                .defineListAllowEmpty(Collections.singletonList("blacklistedResourcePacks"),
                        () -> List.of("Xray_Ultimate_1.21_v5.0.4.zip", "blank.zip"),
                        entry -> entry instanceof String && !((String) entry).isBlank());

        blacklistedItems = builder.comment("Fully qualified item resource locations that players are not allowed to carry (e.g., minecraft:command_block).")
                .translation("wildernessodysseyapi.anti_cheat.blacklisted_items")
                .defineListAllowEmpty(Collections.singletonList("blacklistedItems"),
                        List::of,
                        entry -> entry instanceof String && !((String) entry).isBlank());

        builder.pop();
    }

    public boolean kicksEnabled() {
        return enableKicks.get();
    }

    public boolean logDetections() {
        return logDetections.get();
    }

    public Set<String> blacklistedModIds() {
        return normalize(blacklistedMods.get());
    }

    public Set<String> blacklistedResourcePackIds() {
        return normalize(blacklistedResourcePacks.get());
    }

    public Set<String> blacklistedItemIds() {
        return normalize(blacklistedItems.get());
    }

    private Set<String> normalize(List<? extends String> source) {
        return source.stream()
                .map(String::trim)
                .filter(entry -> !entry.isEmpty())
                .map(entry -> entry.toLowerCase(Locale.ROOT))
                .collect(Collectors.toUnmodifiableSet());
    }

}
