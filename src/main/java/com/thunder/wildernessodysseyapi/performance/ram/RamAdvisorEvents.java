package com.thunder.wildernessodysseyapi.performance.ram;

import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

@EventBusSubscriber(modid = "wildernessodysseyapi")
public final class RamAdvisorEvents {
    private RamAdvisorEvents() {}

    @SubscribeEvent
    public static void onServerStarted(ServerStartedEvent event) {
        RamAdvisorService.get().start();
    }

    @SubscribeEvent
    public static void onServerStopping(ServerStoppingEvent event) {
        RamAdvisorService.get().stop();
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        RamAdvisorService.get().tick();
    }

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        registerCommands(event.getDispatcher());
    }

    private static void registerCommands(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("wo")
                .then(Commands.literal("ram")
                        .executes(ctx -> showReport(ctx.getSource()))
                        .then(Commands.literal("status")
                                .executes(ctx -> showReport(ctx.getSource())))
                        .then(Commands.literal("reset")
                                .requires(source -> source.hasPermission(2))
                                .executes(ctx -> {
                                    RamAdvisorService.get().reset();
                                    ctx.getSource().sendSuccess(() -> Component.literal("RAM Advisor measurements reset."), false);
                                    return 1;
                                }))));
    }

    private static int showReport(CommandSourceStack source) {
        RamAdvisorService service = RamAdvisorService.get();
        RamRecommendation recommendation = service.recommendation();
        for (String line : RamAdvisorFormatter.format(recommendation, service.sampleCount(), service.gcSampleCount())) {
            source.sendSuccess(() -> Component.literal(line), false);
        }
        return 1;
    }
}
