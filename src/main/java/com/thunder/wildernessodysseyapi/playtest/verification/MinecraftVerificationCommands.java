package com.thunder.wildernessodysseyapi.playtest.verification;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.thunder.wildernessodysseyapi.core.ModConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterClientCommandsEvent;

import java.util.Optional;

@EventBusSubscriber(value = Dist.CLIENT, modid = ModConstants.MOD_ID)
public final class MinecraftVerificationCommands {
    private MinecraftVerificationCommands() {
    }

    @SubscribeEvent
    public static void onRegisterClientCommands(RegisterClientCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> dispatcher = event.getDispatcher();
        dispatcher.register(Commands.literal("wo")
                .then(Commands.literal("link")
                        .executes(context -> showHelp(context.getSource()))
                        .then(Commands.literal("status")
                                .executes(context -> showStatus(context.getSource())))
                        .then(Commands.literal("clear")
                                .executes(context -> clearLinkedAccount(context.getSource())))
                        .then(Commands.literal("help")
                                .executes(context -> showHelp(context.getSource())))
                        .then(Commands.argument("code", StringArgumentType.word())
                                .executes(context -> linkWithCode(
                                        context.getSource(),
                                        StringArgumentType.getString(context, "code")
                                )))));
    }

    private static int linkWithCode(CommandSourceStack source, String code) {
        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;
        if (player == null) {
            source.sendFailure(Component.literal("Join a world before linking your Discord account."));
            return 0;
        }

        String minecraftUuid = player.getUUID().toString();
        String minecraftName = player.getGameProfile().getName();

        source.sendSuccess(() -> Component.literal("Checking your Discord verification code..."), false);
        MinecraftVerificationService.verifyCode(code, minecraftUuid, minecraftName)
                .whenComplete((outcome, throwable) -> minecraft.execute(() -> {
                    if (throwable != null) {
                        ModConstants.LOGGER.warn("[MinecraftVerification] Unexpected verification failure: {}", throwable.getMessage());
                        sendToPlayer(Component.literal("Verification failed because something went wrong. Please try again."));
                        return;
                    }
                    if (!outcome.ok()) {
                        sendToPlayer(Component.literal("Verification failed: " + outcome.failureMessage()));
                        return;
                    }
                    if (!outcome.rememberEnabled()) {
                        sendToPlayer(Component.literal("Discord account verified. Local remembering is disabled, so this link was not saved."));
                        return;
                    }
                    if (!outcome.stored()) {
                        sendToPlayer(Component.literal("Discord account verified, but the local linked state could not be saved."));
                        return;
                    }
                    sendToPlayer(Component.literal("Discord account linked for " + outcome.account().minecraftName() + "."));
                }));
        return 1;
    }

    private static int showStatus(CommandSourceStack source) {
        Optional<LinkedMinecraftAccount> account = MinecraftVerificationService.linkedAccount();
        if (account.isEmpty()) {
            source.sendSuccess(() -> Component.literal("This client is not linked. Run /wo link help to get started."), false);
            return 1;
        }

        LinkedMinecraftAccount linked = account.get();
        source.sendSuccess(() -> Component.literal(
                "Linked to Discord user " + linked.discordUserId()
                        + " as " + linked.minecraftName()
                        + " (" + linked.minecraftUuid() + ") since " + linked.verifiedAt() + "."
        ), false);
        return 1;
    }

    private static int clearLinkedAccount(CommandSourceStack source) {
        boolean cleared = MinecraftVerificationService.clearLinkedAccount();
        source.sendSuccess(() -> Component.literal(cleared
                ? "Local linked account state cleared."
                : "No local linked account state was saved."), false);
        return 1;
    }

    private static int showHelp(CommandSourceStack source) {
        source.sendSuccess(() -> Component.literal("Discord linking: run /minecraft link in Discord, copy the code, then run /wo link CODE in Minecraft."), false);
        source.sendSuccess(() -> Component.literal("Use /wo link status to check this client or /wo link clear to forget the local link."), false);
        return 1;
    }

    private static void sendToPlayer(Component component) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player != null) {
            minecraft.player.sendSystemMessage(component);
        }
    }
}
