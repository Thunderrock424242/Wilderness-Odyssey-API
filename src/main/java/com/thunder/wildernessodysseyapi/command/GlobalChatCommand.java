package com.thunder.wildernessodysseyapi.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.LongArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.thunder.wildernessodysseyapi.globalchat.GlobalChatManager;
import com.thunder.wildernessodysseyapi.globalchat.GlobalChatOptIn;
import com.thunder.wildernessodysseyapi.globalchat.GlobalChatServerProcess;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import java.time.Duration;
import java.time.format.DateTimeFormatter;

/**
 * Entry point for global chat commands.
 */
public class GlobalChatCommand {

    private static final GlobalChatServerProcess SERVER_PROCESS = new GlobalChatServerProcess();

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("globalchat")
                .then(Commands.literal("bind")
                        .then(Commands.argument("host", StringArgumentType.string())
                                .then(Commands.argument("port", IntegerArgumentType.integer(1, 65535))
                                        .executes(GlobalChatCommand::bind))))
                .then(Commands.literal("status").executes(GlobalChatCommand::status))
                .then(Commands.literal("optin")
                        .then(Commands.argument("enabled", BoolArgumentType.bool())
                                .executes(GlobalChatCommand::optIn)))
                .then(Commands.literal("send")
                        .then(Commands.argument("message", StringArgumentType.greedyString())
                                .executes(GlobalChatCommand::send)))
                .then(Commands.literal("startserver")
                        .requires(source -> source.hasPermission(2))
                        .then(Commands.argument("port", IntegerArgumentType.integer(1, 65535))
                                .executes(GlobalChatCommand::startServer)))
                .then(Commands.literal("stopserver")
                        .requires(source -> source.hasPermission(2))
                        .executes(GlobalChatCommand::stopServer))
                .then(Commands.literal("moderationtoken")
                        .requires(source -> source.hasPermission(2))
                        .then(Commands.argument("token", StringArgumentType.string())
                                .executes(GlobalChatCommand::setModerationToken)))
                .then(Commands.literal("allowautostart")
                        .requires(source -> source.hasPermission(2))
                        .then(Commands.argument("enabled", BoolArgumentType.bool())
                                .executes(GlobalChatCommand::setAllowAutostart)))
                .then(Commands.literal("mod")
                        .requires(source -> source.hasPermission(2))
                        .then(Commands.literal("ban")
                                .then(Commands.argument("target", StringArgumentType.string())
                                        .then(Commands.argument("durationSeconds", IntegerArgumentType.integer(0))
                                                .executes(ctx -> ban(ctx, ""))
                                                .then(Commands.argument("reason", StringArgumentType.greedyString())
                                                        .executes(ctx -> ban(ctx, StringArgumentType.getString(ctx, "reason")))))))
                        .then(Commands.literal("unban")
                                .then(Commands.argument("target", StringArgumentType.string())
                                        .executes(GlobalChatCommand::unban)))
                        .then(Commands.literal("timeout")
                                .then(Commands.argument("target", StringArgumentType.string())
                                        .then(Commands.argument("durationSeconds", IntegerArgumentType.integer(1))
                                                .executes(ctx -> timeout(ctx, ""))
                                                .then(Commands.argument("reason", StringArgumentType.greedyString())
                                                        .executes(ctx -> timeout(ctx, StringArgumentType.getString(ctx, "reason")))))))
                        .then(Commands.literal("ipban")
                                .requires(source -> source.hasPermission(3))
                                .then(Commands.argument("ip", StringArgumentType.string())
                                        .then(Commands.argument("durationSeconds", LongArgumentType.longArg(0))
                                                .executes(ctx -> ipBan(ctx, ""))
                                                .then(Commands.argument("reason", StringArgumentType.greedyString())
                                                        .executes(ctx -> ipBan(ctx, StringArgumentType.getString(ctx, "reason")))))))
                        .then(Commands.literal("ipunban")
                                .requires(source -> source.hasPermission(3))
                                .then(Commands.argument("ip", StringArgumentType.string())
                                        .executes(GlobalChatCommand::ipUnban)))
                        .then(Commands.literal("list")
                                .executes(ctx -> listConnections(ctx, false))
                                .then(Commands.literal("withip")
                                        .requires(source -> source.hasPermission(3))
                                        .executes(ctx -> listConnections(ctx, true))))
                        .then(Commands.literal("role")
                                .then(Commands.argument("serverId", StringArgumentType.string())
                                        .then(Commands.argument("role", StringArgumentType.string())
                                                .executes(GlobalChatCommand::assignRole)))))
        );
    }

    private static int bind(CommandContext<CommandSourceStack> ctx) {
        String host = StringArgumentType.getString(ctx, "host");
        int port = IntegerArgumentType.getInteger(ctx, "port");
        GlobalChatManager.getInstance().bind(host, port);
        ctx.getSource().sendSuccess(() -> Component.literal("Global chat bound to " + host + ":" + port), true);
        return 1;
    }

    private static int status(CommandContext<CommandSourceStack> ctx) {
        GlobalChatManager manager = GlobalChatManager.getInstance();
        boolean connected = manager.isConnected();
        String ping = manager.lastPing().map(Duration::toMillis).map(ms -> ms + "ms").orElse("n/a");
        String lastConnected = manager.lastConnectedAt().map(DateTimeFormatter.ISO_INSTANT::format).orElse("never");
        String lastDisconnected = manager.lastDisconnectedAt().map(DateTimeFormatter.ISO_INSTANT::format).orElse("never");
        ctx.getSource().sendSuccess(() -> Component.literal("Global chat status: " + (connected ? "connected" : "disconnected")), false);
        ctx.getSource().sendSuccess(() -> Component.literal("Ping: " + ping), false);
        ctx.getSource().sendSuccess(() -> Component.literal("Last up: " + lastConnected + " | Last down: " + lastDisconnected), false);
        manager.getSettings().downtimeHistory().forEach(entry ->
                ctx.getSource().sendSuccess(() -> Component.literal("Downtime: " + entry), false));
        ServerPlayer player = ctx.getSource().getPlayer();
        if (player != null) {
            boolean optedIn = GlobalChatOptIn.isOptedIn(player);
            ctx.getSource().sendSuccess(() -> Component.literal("Opt-in: " + (optedIn ? "enabled" : "disabled")), false);
        }
        return 1;
    }

    private static int send(CommandContext<CommandSourceStack> ctx) {
        ServerPlayer player = ctx.getSource().getPlayer();
        if (player == null) {
            ctx.getSource().sendFailure(Component.literal("Global chat messages must be sent by a player."));
            return 0;
        }
        if (!GlobalChatOptIn.isOptedIn(player)) {
            ctx.getSource().sendFailure(Component.literal("You must opt into global chat before sending messages."));
            return 0;
        }
        String message = StringArgumentType.getString(ctx, "message");
        GlobalChatManager.getInstance().sendChat(player.getGameProfile().getName(), message);
        ctx.getSource().sendSuccess(() -> Component.literal("Sent to global chat."), false);
        return 1;
    }

    private static int optIn(CommandContext<CommandSourceStack> ctx) {
        ServerPlayer player = ctx.getSource().getPlayer();
        if (player == null) {
            ctx.getSource().sendFailure(Component.literal("Only players can change their opt-in status."));
            return 0;
        }
        boolean enabled = BoolArgumentType.getBool(ctx, "enabled");
        GlobalChatOptIn.setOptIn(player, enabled);
        ctx.getSource().sendSuccess(() -> Component.literal("Global chat opt-in set to " + (enabled ? "enabled" : "disabled")), false);
        return 1;
    }

    private static int startServer(CommandContext<CommandSourceStack> ctx) {
        int port = IntegerArgumentType.getInteger(ctx, "port");
        if (GlobalChatManager.getInstance().getSettings() != null
                && !GlobalChatManager.getInstance().getSettings().allowServerAutostart()) {
            ctx.getSource().sendFailure(Component.literal("Relay autostart is disabled; start the central server on the dedicated host you configured."));
            return 0;
        }
        try {
            SERVER_PROCESS.start(port);
            ctx.getSource().sendSuccess(() -> Component.literal("Started relay server on port " + port), true);
            return 1;
        } catch (Exception e) {
            ctx.getSource().sendFailure(Component.literal("Unable to start relay server: " + e.getMessage()));
            return 0;
        }
    }

    private static int stopServer(CommandContext<CommandSourceStack> ctx) {
        SERVER_PROCESS.stop();
        ctx.getSource().sendSuccess(() -> Component.literal("Stopped relay server."), true);
        return 1;
    }

    private static int setModerationToken(CommandContext<CommandSourceStack> ctx) {
        String token = StringArgumentType.getString(ctx, "token");
        GlobalChatManager.getInstance().setModerationToken(token);
        ctx.getSource().sendSuccess(() -> Component.literal("Updated moderation token for global chat."), true);
        return 1;
    }

    private static int setAllowAutostart(CommandContext<CommandSourceStack> ctx) {
        boolean enabled = BoolArgumentType.getBool(ctx, "enabled");
        GlobalChatManager manager = GlobalChatManager.getInstance();
        if (manager.getSettings() != null) {
            manager.getSettings().setAllowServerAutostart(enabled);
        }
        ctx.getSource().sendSuccess(() -> Component.literal("Relay server autostart " + (enabled ? "enabled" : "disabled") + "."), true);
        return 1;
    }

    private static int ban(CommandContext<CommandSourceStack> ctx, String reason) {
        if (!ensureModerationReady(ctx)) {
            return 0;
        }
        String target = StringArgumentType.getString(ctx, "target");
        int durationSeconds = IntegerArgumentType.getInteger(ctx, "durationSeconds");
        GlobalChatManager.getInstance().sendModerationAction("ban", target, durationSeconds, false, null, null, reason);
        ctx.getSource().sendSuccess(() -> Component.literal("Ban request sent for " + target), true);
        return 1;
    }

    private static int unban(CommandContext<CommandSourceStack> ctx) {
        if (!ensureModerationReady(ctx)) {
            return 0;
        }
        String target = StringArgumentType.getString(ctx, "target");
        GlobalChatManager.getInstance().sendModerationAction("unban", target, 0, false, null, null, "");
        ctx.getSource().sendSuccess(() -> Component.literal("Unban request sent for " + target), true);
        return 1;
    }

    private static int timeout(CommandContext<CommandSourceStack> ctx, String reason) {
        if (!ensureModerationReady(ctx)) {
            return 0;
        }
        String target = StringArgumentType.getString(ctx, "target");
        int durationSeconds = IntegerArgumentType.getInteger(ctx, "durationSeconds");
        GlobalChatManager.getInstance().sendModerationAction("timeout", target, durationSeconds, false, null, null, reason);
        ctx.getSource().sendSuccess(() -> Component.literal("Timeout request sent for " + target), true);
        return 1;
    }

    private static int ipBan(CommandContext<CommandSourceStack> ctx, String reason) {
        if (!ensureModerationReady(ctx)) {
            return 0;
        }
        String ip = StringArgumentType.getString(ctx, "ip");
        long durationSeconds = LongArgumentType.getLong(ctx, "durationSeconds");
        GlobalChatManager.getInstance().sendModerationAction("ipban", ip, durationSeconds, false, null, ip, reason);
        ctx.getSource().sendSuccess(() -> Component.literal("IP ban request sent for " + ip), true);
        return 1;
    }

    private static int ipUnban(CommandContext<CommandSourceStack> ctx) {
        if (!ensureModerationReady(ctx)) {
            return 0;
        }
        String ip = StringArgumentType.getString(ctx, "ip");
        GlobalChatManager.getInstance().sendModerationAction("ipunban", ip, 0, false, null, ip, "");
        ctx.getSource().sendSuccess(() -> Component.literal("IP unban request sent for " + ip), true);
        return 1;
    }

    private static int listConnections(CommandContext<CommandSourceStack> ctx, boolean includeIp) {
        if (!ensureModerationReady(ctx)) {
            return 0;
        }
        GlobalChatManager.getInstance().sendModerationAction("list", "", 0, includeIp, null, null, "");
        ctx.getSource().sendSuccess(() -> Component.literal("Requested connection list from relay."), false);
        return 1;
    }

    private static int assignRole(CommandContext<CommandSourceStack> ctx) {
        if (!ensureModerationReady(ctx)) {
            return 0;
        }
        String serverId = StringArgumentType.getString(ctx, "serverId");
        String role = StringArgumentType.getString(ctx, "role");
        GlobalChatManager.getInstance().sendModerationAction("role", serverId, 0, false, role, null, "");
        ctx.getSource().sendSuccess(() -> Component.literal("Role update sent for " + serverId), true);
        return 1;
    }

    private static boolean ensureModerationReady(CommandContext<CommandSourceStack> ctx) {
        GlobalChatManager manager = GlobalChatManager.getInstance();
        if (!manager.isConnected()) {
            ctx.getSource().sendFailure(Component.literal("Not connected to a relay server."));
            return false;
        }
        if (manager.getSettings() == null || manager.getSettings().moderationToken().isEmpty()) {
            ctx.getSource().sendFailure(Component.literal("Set a moderation token before issuing admin actions."));
            return false;
        }
        return true;
    }
}
