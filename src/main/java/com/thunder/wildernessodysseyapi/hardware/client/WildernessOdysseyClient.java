package com.thunder.wildernessodysseyapi.hardware.client;

import com.thunder.wildernessodysseyapi.Core.ModConstants;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.thunder.wildernessodysseyapi.hardware.client.command.AutoShaderCommand;
import com.thunder.wildernessodysseyapi.hardware.client.gui.HardwareRequirementScreen;
import com.thunder.wildernessodysseyapi.hardware.client.hardware.HardwareRequirementChecker;
import com.thunder.wildernessodysseyapi.hardware.client.hardware.HardwareRequirementConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.RegisterClientCommandsEvent;

import java.util.Arrays;
import java.util.Locale;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Client entry point for Wilderness Odyssey specific features.
 */
@EventBusSubscriber(modid = ModConstants.MOD_ID, value = Dist.CLIENT)
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

    @EventBusSubscriber(modid = ModConstants.MOD_ID, value = Dist.CLIENT)
    public static final class ClientEvents {
        private ClientEvents() {
        }

        @SubscribeEvent
        public static void registerClientCommands(RegisterClientCommandsEvent event) {
            event.getDispatcher().register(Commands.literal("hardwarecheck")
                .executes(context -> executeOpenScreen(context.getSource())));
            event.getDispatcher().register(Commands.literal("autoshader")
                .executes(context -> executeAutoShader(context.getSource()))
                .then(Commands.argument("tier", StringArgumentType.word())
                    .suggests((context, builder) -> SharedSuggestionProvider.suggest(Arrays.stream(HardwareRequirementConfig.Tier.values())
                        .map(tier -> tier.name().toLowerCase(Locale.ROOT)), builder))
                    .executes(context -> executeAutoShader(context.getSource(), StringArgumentType.getString(context, "tier")))));
        }

        private static int executeOpenScreen(CommandSourceStack source) {
            openHardwareScreen();
            source.sendSuccess(() -> Component.translatable("command.wildernessodyssey.hardware.open"), true);
            return 1;
        }

        private static int executeAutoShader(CommandSourceStack source) {
            return AutoShaderCommand.execute(source, getOrCreateChecker());
        }

        private static int executeAutoShader(CommandSourceStack source, String tierName) {
            Optional<HardwareRequirementConfig.Tier> tier = parseTier(tierName);
            if (tier.isEmpty()) {
                String valid = Arrays.stream(HardwareRequirementConfig.Tier.values())
                    .map(value -> value.name().toLowerCase(Locale.ROOT))
                    .collect(Collectors.joining(", "));
                source.sendFailure(Component.translatable("command.wildernessodyssey.autoshader.unknown_tier", tierName, valid));
                return 0;
            }

            return AutoShaderCommand.execute(source, getOrCreateChecker(), tier.get());
        }

        private static Optional<HardwareRequirementConfig.Tier> parseTier(String name) {
            for (HardwareRequirementConfig.Tier tier : HardwareRequirementConfig.Tier.values()) {
                if (tier.name().equalsIgnoreCase(name) || tier.configKey().equalsIgnoreCase(name)) {
                    return Optional.of(tier);
                }
            }
            return Optional.empty();
        }
    }
}
