package com.thunder.wildernessodysseyapi.watersystem.water.render;

import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterClientCommandsEvent;

@EventBusSubscriber(value = Dist.CLIENT, modid = "wildernessodysseyapi")
public final class TideHudClientCommand {

    private TideHudClientCommand() {}

    @SubscribeEvent
    public static void onRegisterClientCommands(RegisterClientCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> dispatcher = event.getDispatcher();

        dispatcher.register(
                Commands.literal("tidehud")
                        .executes(ctx -> sendStatus(ctx.getSource()))
                        .then(Commands.literal("on").executes(ctx -> setEnabled(ctx.getSource(), true)))
                        .then(Commands.literal("off").executes(ctx -> setEnabled(ctx.getSource(), false)))
                        .then(Commands.literal("toggle").executes(ctx -> toggle(ctx.getSource())))
                        .then(Commands.literal("status").executes(ctx -> sendStatus(ctx.getSource())))
        );
    }

    private static int setEnabled(CommandSourceStack source, boolean enabled) {
        TideHudOverlay.setHudEnabled(enabled);
        source.sendSuccess(() -> Component.literal(enabled
                ? "🌊 Tide HUD enabled."
                : "🌊 Tide HUD disabled."), false);
        return 1;
    }

    private static int toggle(CommandSourceStack source) {
        boolean enabled = TideHudOverlay.toggleHudEnabled();
        source.sendSuccess(() -> Component.literal(enabled
                ? "🌊 Tide HUD enabled."
                : "🌊 Tide HUD disabled."), false);
        return 1;
    }

    private static int sendStatus(CommandSourceStack source) {
        boolean enabled = TideHudOverlay.isHudEnabled();
        source.sendSuccess(() -> Component.literal("🌊 Tide HUD is currently "
                + (enabled ? "enabled" : "disabled")
                + ". Use /tidehud on|off|toggle."), false);
        return 1;
    }
}
