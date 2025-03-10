package com.thunder.wildernessodysseyapi.NovaAPI.commands;

import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.UUID;
import java.util.function.Supplier;

public class NovaAPICommand {
    private static final File REQUEST_FILE = new File("whitelist_requests.txt");

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("novaapi")
                .requires(source -> source.hasPermission(2)) // OP-only
                .then(Commands.literal("requestAccess")
                        .executes(ctx -> {
                            String serverIP = ctx.getSource().getServer().getLocalIp();
                            String requestId = UUID.randomUUID().toString().substring(0, 8); // Unique request ID

                            boolean success = saveWhitelistRequest(requestId, serverIP);
                            if (success) {
                                ctx.getSource().sendSuccess((Supplier<Component>) Component.literal("[Nova API] Whitelist request sent! Request ID: " + requestId), false);
                            } else {
                                ctx.getSource().sendFailure(Component.literal("[Nova API] Failed to send request. Try again later."));
                            }
                            return 1;
                        })));
    }

    private static boolean saveWhitelistRequest(String id, String ip) {
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(REQUEST_FILE, true))) {
            writer.write(id + ":" + ip + "\n");
        } catch (IOException e) {
            return false;
        }
        return true;
    }
}