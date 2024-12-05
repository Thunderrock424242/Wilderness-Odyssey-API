package com.thunder.wildernessodysseyapi.AntiCheat;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

import java.util.Arrays;
import java.util.List;

public class BlacklistChecker {

    // Hardcoded list of blacklisted mods and resource packs
    private static final List<String> BLACKLISTED_MODS = Arrays.asList(
            "examplemod1", // Replace with real mod IDs
            "examplemod2"
    );

    private static final List<String> BLACKLISTED_RESOURCE_PACKS = Arrays.asList(
            "Xray_Ultimate_1.21_v5.0.4.zip", // Replace with real resource pack file names
            "badpack2.zip"
    );

    public BlacklistChecker() {
        // Register the event listener
        NeoForge.EVENT_BUS.register(this);
    }

    @SubscribeEvent
    public void onPlayerLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            if (checkBlacklistedMods(player) || checkBlacklistedResourcePacks(player)) {
                // Kick the player if any blacklist condition is met
                player.connection.disconnect(Component.literal(
                        "Error: Blacklisted mods or resource packs detected. Remove them and try again." +
                                "if you added any mods remove them or install the pack again." +
                                "if its still not working contact server support or modpack creator team."
                ));
            }
        }
    }

    private boolean checkBlacklistedMods(ServerPlayer player) {
        for (String modId : BLACKLISTED_MODS) {
            if (ModList.get().isLoaded(modId)) {
                return true; // Blacklisted mod detected
            }
        }
        return false;
    }

    private boolean checkBlacklistedResourcePacks(ServerPlayer player) {
        var server = player.server;
        server.getPackRepository();
        var loadedPacks = server.getPackRepository().getSelectedPacks();
        for (var pack : loadedPacks) {
            if (BLACKLISTED_RESOURCE_PACKS.contains(pack.getId())) {
                return true; // Blacklisted resource pack detected
            }
        }
        return false;
    }
}
