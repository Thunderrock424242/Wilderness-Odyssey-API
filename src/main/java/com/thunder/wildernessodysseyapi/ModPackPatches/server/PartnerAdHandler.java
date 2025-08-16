package com.thunder.wildernessodysseyapi.ModPackPatches.server;

import com.mojang.brigadier.CommandDispatcher;
import com.thunder.wildernessodysseyapi.Core.ModConstants;
import com.thunder.wildernessodysseyapi.ModPackPatches.client.WorldVersionChecker;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.TextColor;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.PlayerList;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

@EventBusSubscriber
/**
 * Sends occasional partner advertisements to players.
 */
public class PartnerAdHandler {
    private static final long TICKS_PER_HOUR = 20L * 60L * 60L;
    private static long tickCounter = 0;

    private static final Set<UUID> OPTED_OUT = new HashSet<>();
    private static final String TAG_OPT_OUT = "partnerad_optout";
    private static final String TAG_VERSION = "partnerad_version";

    private static final String PARTNER_NAME = "Kinetic_Hosting";
    private static final String PARTNER_CODE = "THUNDER";
    private static final String DISCOUNT = "15%";
    private static final String HOSTING_LINK = "https://billing.kinetichosting.net/aff.php?aff=606";

    private static final long DELAY_TICKS = 20L * 90L; // 1.5 minutes
    private static final Set<UUID> waitingPlayers = new HashSet<>();
    private static final Set<UUID> sentAdPlayers = new HashSet<>();
    private static final java.util.Map<UUID, Long> playerTickMap = new java.util.HashMap<>();

    /**
     * Build the styled advertisement message with clickable opt-in/out.
     */
    private static Component makeAd(ServerPlayer player) {
        Component msg = Component.literal("Need your own Minecraft server? ")
                .withStyle(style -> style.withColor(TextColor.fromRgb(0xFFD700)));

        msg = ((net.minecraft.network.chat.MutableComponent) msg)
                .append(Component.literal("We’ve teamed up with ")
                        .withStyle(style -> style.withColor(TextColor.fromRgb(0x00FFFF))))
                .append(Component.literal(PARTNER_NAME)
                        .withStyle(style -> style.withColor(TextColor.fromRgb(0x00FF00))))
                .append(Component.literal(" — get ")
                        .withStyle(style -> style.withColor(TextColor.fromRgb(0x00FFFF))))
                .append(Component.literal(DISCOUNT + " off")
                        .withStyle(style -> style.withColor(TextColor.fromRgb(0x00FF00))))
                .append(Component.literal(" when you use code ")
                        .withStyle(style -> style.withColor(TextColor.fromRgb(0x00FFFF))))
                .append(Component.literal(PARTNER_CODE)
                        .withStyle(style -> style.withColor(TextColor.fromRgb(0xFFAA00))))
                .append(Component.literal(" at ")
                        .withStyle(style -> style.withColor(TextColor.fromRgb(0x00FFFF))))
                .append(Component.literal(HOSTING_LINK)
                        .withStyle(style -> style.withColor(TextColor.fromRgb(0x00FF00))
                                .withUnderlined(true)
                                .withClickEvent(new ClickEvent(ClickEvent.Action.OPEN_URL, "https://billing.kinetichosting.net/aff.php?aff=606"))
                        )
                );

        if (OPTED_OUT.contains(player.getUUID())) {
            msg = ((net.minecraft.network.chat.MutableComponent) msg).append(
                    Component.literal(" [Opt In]")
                            .withStyle(style -> style
                                    .withColor(TextColor.fromRgb(0x00FF00))
                                    .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/partnerad optin"))
                                    .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                                            Component.literal("Re-enable these ads"))))
            );
        } else {
            msg = ((net.minecraft.network.chat.MutableComponent) msg).append(
                    Component.literal(" [Opt Out]")
                            .withStyle(style -> style
                                    .withColor(TextColor.fromRgb(0xFF5555))
                                    .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/partnerad optout"))
                                    .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                                            Component.literal("Disable these ads"))))
            );
        }

        return msg;
    }

    /**
     * Register "/partnerad optout" and "/partnerad optin" commands.
     */
    @SubscribeEvent
    public static void registerCommands(RegisterCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> dispatcher = event.getDispatcher();
        dispatcher.register(
                Commands.literal("partnerad")
                        .then(Commands.literal("optout")
                                .executes(ctx -> {
                                    ServerPlayer player = ctx.getSource().getPlayerOrException();
                                    CompoundTag data = player.getPersistentData();
                                    data.putBoolean(TAG_OPT_OUT, true);
                                    data.putString(TAG_VERSION, ModConstants.MOD_DEFAULT_WORLD_VERSION);
                                    OPTED_OUT.add(player.getUUID());
                                    player.sendSystemMessage(
                                            Component.literal("Ads disabled.")
                                                    .withStyle(style -> style.withColor(TextColor.fromRgb(0xFF5555)))
                                    );
                                    return 1;
                                })
                        )
                        .then(Commands.literal("optin")
                                .executes(ctx -> {
                                    ServerPlayer player = ctx.getSource().getPlayerOrException();
                                    CompoundTag data = player.getPersistentData();
                                    data.putBoolean(TAG_OPT_OUT, false);
                                    data.putString(TAG_VERSION, ModConstants.MOD_DEFAULT_WORLD_VERSION);
                                    OPTED_OUT.remove(player.getUUID());
                                    player.sendSystemMessage(
                                            Component.literal("Ads enabled.")
                                                    .withStyle(style -> style.withColor(TextColor.fromRgb(0x00FF00)))
                                    );
                                    return 1;
                                })
                        )
        );
    }

    /**
     * Send ad message when a player logs in (unless they opted out).
     */
    @SubscribeEvent
    public static void onPlayerLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;

        UUID uuid = player.getUUID();
        CompoundTag data = player.getPersistentData();
        String currentVersion = ModConstants.MOD_DEFAULT_WORLD_VERSION;
        String storedVersion = data.getString(TAG_VERSION);
        if (!storedVersion.equals(currentVersion)) {
            data.putBoolean(TAG_OPT_OUT, false);
            data.putString(TAG_VERSION, currentVersion);
        }

        if (data.getBoolean(TAG_OPT_OUT)) {
            OPTED_OUT.add(uuid);
        } else {
            OPTED_OUT.remove(uuid);
            waitingPlayers.add(uuid);
            playerTickMap.put(uuid, tickCounter);
        }
    }

    /**
     * Broadcast ad hourly to all non-opted-out players.
     */
    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        tickCounter++;
        if (!waitingPlayers.isEmpty()) {
            MinecraftServer server = event.getServer();
            PlayerList players = server.getPlayerList();

            for (ServerPlayer player : players.getPlayers()) {
                UUID uuid = player.getUUID();

                if (waitingPlayers.contains(uuid)) {
                    long joinedAt = playerTickMap.getOrDefault(uuid, -1L);
                    if (joinedAt != -1 && tickCounter - joinedAt >= DELAY_TICKS) {
                        player.sendSystemMessage(makeAd(player));
                        waitingPlayers.remove(uuid);
                        sentAdPlayers.add(uuid);
                    }
                }
            }
        }
    }
}
