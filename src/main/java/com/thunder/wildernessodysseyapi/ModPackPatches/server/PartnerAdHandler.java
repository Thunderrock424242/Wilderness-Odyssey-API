package com.thunder.wildernessodysseyapi.ModPackPatches.server;

import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
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
public class PartnerAdHandler {
    private static final long TICKS_PER_HOUR = 20L * 60L * 60L;
    private static long tickCounter = 0;

    // Tracks players who have opted out
    private static final Set<UUID> OPTED_OUT = new HashSet<>();

    // Partnership details—update these constants!
    private static final String PARTNER_NAME  = "Kinetic_Hosting";
    private static final String PARTNER_CODE  = "THUNDER";
    private static final String DISCOUNT      = "15%";
    private static final String HOSTING_LINK  = "https://billing.kinetichosting.net/aff.php?aff=606";

    /**
     * Build the styled advertisement message with clickable opt-in/out.
     */
    private static Component makeAd(ServerPlayer player) {
        // Base text
        Component msg = Component.literal("Need your own Minecraft server? ")
                .withStyle(style -> style.withColor(TextColor.fromRgb(0xFFD700))); // gold

        // Build the rest of the message
        msg = ((net.minecraft.network.chat.MutableComponent) msg)
                .append(Component.literal("We’ve teamed up with ")
                        .withStyle(style -> style.withColor(TextColor.fromRgb(0x00FFFF)))) // aqua
                .append(Component.literal(PARTNER_NAME)
                        .withStyle(style -> style.withColor(TextColor.fromRgb(0x00FF00)))) // lime
                .append(Component.literal(" — get ")
                        .withStyle(style -> style.withColor(TextColor.fromRgb(0x00FFFF))))
                .append(Component.literal(DISCOUNT + " off")
                        .withStyle(style -> style.withColor(TextColor.fromRgb(0x00FF00)))) // lime
                .append(Component.literal(" when you use code ")
                        .withStyle(style -> style.withColor(TextColor.fromRgb(0x00FFFF))))
                .append(Component.literal(PARTNER_CODE)
                        .withStyle(style -> style.withColor(TextColor.fromRgb(0xFFAA00)))) // orange
                .append(Component.literal(" at ")
                        .withStyle(style -> style.withColor(TextColor.fromRgb(0x00FFFF))))
                .append(Component.literal(HOSTING_LINK)
                        .withStyle(style -> style.withColor(TextColor.fromRgb(0x00FF00))
                                .withUnderlined(true)
                                .withClickEvent(new ClickEvent(ClickEvent.Action.OPEN_URL, "https://billing.kinetichosting.net/aff.php?aff=606"))
                        )
                );

        // Add opt-in/out toggle
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
        if (!OPTED_OUT.contains(player.getUUID())) {
            player.sendSystemMessage(makeAd(player));
        }
    }

    /**
     * Broadcast ad hourly to all non-opted-out players.
     */
    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        tickCounter++;
        if (event.hasTime() && tickCounter % TICKS_PER_HOUR == 0) {
            MinecraftServer server = event.getServer();
            PlayerList players = server.getPlayerList();
            for (ServerPlayer player : players.getPlayers()) {
                if (!OPTED_OUT.contains(player.getUUID())) {
                    player.sendSystemMessage(makeAd(player));
                }
            }
        }
    }
}
