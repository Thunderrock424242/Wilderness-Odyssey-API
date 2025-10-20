package com.thunder.wildernessodysseyapi.hardware.client;

import com.thunder.wildernessodysseyapi.Core.ModConstants;
import com.thunder.wildernessodysseyapi.hardware.client.command.AutoShaderCommand;
import com.thunder.wildernessodysseyapi.hardware.client.gui.HardwareRequirementScreen;
import com.thunder.wildernessodysseyapi.hardware.client.hardware.HardwareRequirementChecker;
import com.thunder.wildernessodysseyapi.hardware.client.hardware.HardwareRequirementConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.RegisterClientCommandsEvent;

/**
 * Client entry point for Wilderness Odyssey specific features.
 */
@EventBusSubscriber(modid = ModConstants.MOD_ID, value = Dist.CLIENT, bus = EventBusSubscriber.Bus.MOD)
public final class WildernessOdysseyClient {
    private static HardwareRequirementChecker checker;

    private WildernessOdysseyClient() {
    }

    @SubscribeEvent
    public static void onClientSetup(FMLClientSetupEvent event) {
        event.enqueueWork(() -> checker = new HardwareRequirementChecker(HardwareRequirementConfig.load()));
    }

    private static HardwareRequirementChecker getOrCreateChecker() {
        if (checker == null) {
            checker = new HardwareRequirementChecker(HardwareRequirementConfig.load());
        }
        return checker;
    }

    private static void openHardwareScreen() {
        Minecraft minecraft = Minecraft.getInstance();
        minecraft.setScreen(new HardwareRequirementScreen(getOrCreateChecker()));
    }

    @EventBusSubscriber(modid = ModConstants.MOD_ID, value = Dist.CLIENT, bus = EventBusSubscriber.Bus.GAME)
    public static final class ClientEvents {
        private ClientEvents() {
        }

        @SubscribeEvent
        public static void registerClientCommands(RegisterClientCommandsEvent event) {
            event.getDispatcher().register(Commands.literal("hardwarecheck")
                .executes(context -> executeOpenScreen(context.getSource())));
            event.getDispatcher().register(Commands.literal("autoshader")
                .executes(context -> executeAutoShader(context.getSource())));
        }

        private static int executeOpenScreen(CommandSourceStack source) {
            openHardwareScreen();
            source.sendSuccess(() -> Component.translatable("command.wildernessodyssey.hardware.open"), true);
            return 1;
        }

        private static int executeAutoShader(CommandSourceStack source) {
            return AutoShaderCommand.execute(source, getOrCreateChecker());
        }
    }
}
