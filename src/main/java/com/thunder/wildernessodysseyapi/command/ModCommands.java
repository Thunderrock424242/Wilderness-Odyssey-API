package com.thunder.wildernessodysseyapi.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.thunder.wildernessodysseyapi.WildernessOdysseyAPI;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.server.ServerStartingEvent;
import net.neoforged.fml.common.EventBusSubscriber;

/**
 * The type Mod commands.
 */
@EventBusSubscriber(modid = "yourmodid")
public class ModCommands {

    /**
     * On server starting.
     *
     * @param event the event
     */
    @SubscribeEvent
    public static void onServerStarting(ServerStartingEvent event) {
        registerCommands(event.getServer().getCommands().getDispatcher());
    }

    private static void registerCommands(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("setOutlineEnabled")
                .then(Commands.argument("enabled", BoolArgumentType.bool())
                        .executes(context -> {
                            boolean enabled = BoolArgumentType.getBool(context, "enabled");
                            //WildernessOdysseyAPI.ENABLE_OUTLINE = enabled;
                            context.getSource().sendSuccess(
                                    () -> net.minecraft.network.chat.Component.literal("Entity outline feature set to: " + enabled), true);
                            return 1;
                        })
                )
        );
    }
}