/*
 * The code of this mod element is always locked.
 *
 * You can register new events in this class too.
 *
 * If you want to make a plain independent class, create it using
 * Project Browser -> New... and make sure to make the class
 * outside net.mcreator.wildernessoddesyapi as this package is managed by MCreator.
 *
 * If you change workspace package, modid or prefix, you will need
 * to manually adapt this file to these changes or remake it.
 *
 * This class will be added in the mod root package.
*/
package net.mcreator.wildernessodysseyapi;

import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.Mod;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Unmodifiable;

import java.io.FileWriter;
import java.io.IOException;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;

@EventBusSubscriber(modid = "yourmodid")
public class ModWhitelistChecker {

    private static final Set<String> MOD_WHITELIST = Set.of("example_mod_1", "example_mod_2", "required_mod_3");
    private static final Set<String> RESOURCE_PACK_BLACKLIST = Set.of("malicious_pack_1", "cheat_resource_pack");
    private static final String LOCAL_LOG_FILE_PATH = "logs/anticheat-violations.log";

    @SubscribeEvent
    public static void onPlayerLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (!WildernessOdysseyAPI.antiCheatEnabled) {
            return; // Do nothing if anti-cheat is disabled
        }

        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }

        Set<String> loadedMods = ModList.get().getMods().stream()
                .map(mod -> mod.getModId())
                .collect(Collectors.toSet());

        checkMods(player, loadedMods);
        checkResourcePacks(player);
    }

    private static void checkMods(ServerPlayer player, Set<String> loadedMods) {
        Set<String> missingMods = new HashSet<>(MOD_WHITELIST);
        missingMods.removeAll(loadedMods);

        Set<String> unauthorizedMods = new HashSet<>(loadedMods);
        unauthorizedMods.removeAll(MOD_WHITELIST);

        if (!missingMods.isEmpty()) {
            player.sendSystemMessage(Component.literal("Missing required mods: " + String.join(", ", missingMods)));
            player.connection.disconnect(Component.literal("Missing required mods: " + String.join(", ", missingMods)));
            return;
        }

        if (!unauthorizedMods.isEmpty()) {
            player.sendSystemMessage(Component.literal("Unauthorized mods detected: " + String.join(", ", unauthorizedMods)));
            player.connection.disconnect(Component.literal("Unauthorized mods detected: " + String.join(", ", unauthorizedMods)));
        }
    }

    private static void checkResourcePacks(ServerPlayer player) {
        Set<String> activeResourcePacks = getPlayerActiveResourcePacks(player);
        Set<String> blacklistedPacksDetected = new HashSet<>(activeResourcePacks);
        blacklistedPacksDetected.retainAll(RESOURCE_PACK_BLACKLIST);

        if (!blacklistedPacksDetected.isEmpty()) {
            String message = "You are using blacklisted resource packs: " + String.join(", ", blacklistedPacksDetected);
            logViolation(player, blacklistedPacksDetected);
            player.sendSystemMessage(Component.literal(message));
            player.connection.disconnect(Component.literal("Blacklisted resource packs detected: " + String.join(", ", blacklistedPacksDetected)));
        }
    }

    @Contract(value = "_ -> new", pure = true)
    private static @NotNull @Unmodifiable Set<String> getPlayerActiveResourcePacks(ServerPlayer player) {
        // Placeholder logic for active resource packs
        return Set.of("cheat_resource_pack");
    }

    private static void logViolation(@NotNull ServerPlayer player, Set<String> blacklistedPacks) {
        String logEntry = "Player: " + player.getName().getString() + " | UUID: " + player.getStringUUID() +
                " | Detected blacklisted resource packs: " + String.join(", ", blacklistedPacks) + "\n";

        try (FileWriter writer = new FileWriter(LOCAL_LOG_FILE_PATH, true)) {
            writer.write(logEntry);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
