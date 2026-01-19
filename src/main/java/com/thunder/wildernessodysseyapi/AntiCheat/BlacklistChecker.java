package com.thunder.wildernessodysseyapi.AntiCheat;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import static com.thunder.wildernessodysseyapi.Core.ModConstants.LOGGER;

/**
 * Server-side anti-cheat checks for blacklisted content.
 */
public class BlacklistChecker {

    /**
     * Instantiates a new Blacklist checker.
     */
    public BlacklistChecker() {
        NeoForge.EVENT_BUS.register(this);
    }

    /**
     * On player login, validate their environment against anti-cheat rules.
     *
     * @param event the event
     */
    @SubscribeEvent
    public static void onPlayerLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            runChecks(player);
        }
    }

    private static void runChecks(ServerPlayer player) {
        List<String> violations = new ArrayList<>();

        checkBlacklistedMods(player, violations);
        checkBlacklistedResourcePacks(player, violations);
        checkBlacklistedItems(player, collectAllPlayerItems(player), violations);

        if (!violations.isEmpty()) {
            String reason = String.join("; ", violations);
            logDetection(player, reason);

            if (AntiCheatConfig.CONFIG.kicksEnabled()) {
                player.connection.disconnect(Component.literal(reason));
            } else {
                player.sendSystemMessage(Component.literal(reason));
            }
        }
    }

    private static void checkBlacklistedMods(ServerPlayer player, List<String> violations) {
        Set<String> disallowedMods = AntiCheatConfig.CONFIG.blacklistedModIds();
        for (String modId : disallowedMods) {
            if (ModList.get().isLoaded(modId)) {
                violations.add("Blacklisted mod detected: " + modId);
            }
        }
    }

    private static void checkBlacklistedResourcePacks(ServerPlayer player, List<String> violations) {
        Set<String> disallowedPacks = AntiCheatConfig.CONFIG.blacklistedResourcePackIds();
        var loadedPacks = player.server.getPackRepository().getSelectedPacks();
        for (var pack : loadedPacks) {
            String packId = pack.getId().toLowerCase(Locale.ROOT);
            if (disallowedPacks.contains(packId)) {
                violations.add("Blacklisted resource pack active on the server: " + packId);
            }
        }
    }

    private static void checkBlacklistedItems(ServerPlayer player, List<ItemStack> stacks, List<String> violations) {
        Set<String> disallowedItems = AntiCheatConfig.CONFIG.blacklistedItemIds();
        if (disallowedItems.isEmpty()) {
            return;
        }

        for (ItemStack stack : stacks) {
            ResourceLocation itemId = BuiltInRegistries.ITEM.getKey(stack.getItem());
            if (itemId == null) {
                continue;
            }
            String normalizedId = itemId.toString().toLowerCase(Locale.ROOT);
            if (disallowedItems.contains(normalizedId)) {
                stack.setCount(0);
                String reason = "Removed blacklisted item from inventory: " + normalizedId;
                violations.add(reason);
                logDetection(player, reason);
            }
        }
    }

    private static List<ItemStack> collectAllPlayerItems(ServerPlayer player) {
        List<ItemStack> stacks = new ArrayList<>();
        stacks.addAll(player.getInventory().items);
        stacks.addAll(player.getInventory().armor);
        stacks.addAll(player.getInventory().offhand);
        return stacks;
    }

    private static void logDetection(ServerPlayer player, String reason) {
        if (AntiCheatConfig.CONFIG.logDetections()) {
            LOGGER.warn("Anti-cheat triggered for player {}: {}", player.getGameProfile().getName(), reason);
        }
    }
}
