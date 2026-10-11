package com.thunder.wildernessodysseyapi.anomaly.client;

import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.FloatArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.thunder.wildernessodysseyapi.core.ModConstants;
import com.thunder.wildernessodysseyapi.worldgen.anomaly.AnomalyZone;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterClientCommandsEvent;

import java.util.Locale;

/** Local developer previews that never send a packet or mutate server state. */
@EventBusSubscriber(modid = ModConstants.MOD_ID, value = Dist.CLIENT)
public final class AnomalyVisualCommand {
    private AnomalyVisualCommand() {
    }

    @SubscribeEvent
    public static void onRegisterCommands(RegisterClientCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("woanomalyvisual")
                .executes(context -> status(context.getSource()))
                .then(Commands.literal("post")
                        .then(Commands.argument("enabled", BoolArgumentType.bool())
                                .executes(context -> {
                                    boolean enabled = BoolArgumentType.getBool(context, "enabled");
                                    AnomalyClientState.previewPost(enabled);
                                    if (!enabled) {
                                        AnomalyPostEffectController.release();
                                    }
                                    return feedback(context.getSource(), "command.wildernessodysseyapi.anomaly_visual.post", enabled);
                                })))
                .then(Commands.literal("intensity")
                        .then(Commands.literal("reset").executes(context -> reset(context.getSource())))
                        .then(Commands.argument("amount", FloatArgumentType.floatArg(0.0F, 1.0F))
                                .executes(context -> {
                                    float amount = FloatArgumentType.getFloat(context, "amount");
                                    AnomalyClientState.previewIntensity(amount);
                                    return feedback(context.getSource(), "command.wildernessodysseyapi.anomaly_visual.intensity", amount);
                                })))
                .then(Commands.literal("zone")
                        .then(Commands.argument("name", StringArgumentType.word())
                                .suggests((context, builder) -> {
                                    builder.suggest("none");
                                    for (AnomalyZone zone : AnomalyZone.values()) {
                                        builder.suggest(zone.name().toLowerCase(Locale.ROOT));
                                    }
                                    return builder.buildFuture();
                                })
                                .executes(context -> previewZone(context.getSource(), StringArgumentType.getString(context, "name")))))
                .then(Commands.literal("reset").executes(context -> reset(context.getSource()))));
    }

    private static int previewZone(CommandSourceStack source, String name) {
        if ("none".equalsIgnoreCase(name)) {
            AnomalyClientState.previewZone(-1);
            return feedback(source, "command.wildernessodysseyapi.anomaly_visual.zone", "none");
        }
        for (AnomalyZone zone : AnomalyZone.values()) {
            if (zone.name().equalsIgnoreCase(name) || zone.biomePath().equalsIgnoreCase(name)) {
                AnomalyClientState.previewZone(zone.ordinal());
                return feedback(source, "command.wildernessodysseyapi.anomaly_visual.zone", zone.displayName());
            }
        }
        source.sendFailure(Component.translatable("command.wildernessodysseyapi.anomaly_visual.unknown_zone", name));
        return 0;
    }

    private static int reset(CommandSourceStack source) {
        AnomalyClientState.resetPreview();
        return feedback(source, "command.wildernessodysseyapi.anomaly_visual.reset");
    }

    private static int status(CommandSourceStack source) {
        return feedback(source, "command.wildernessodysseyapi.anomaly_visual.status",
                AnomalyClientState.zone(), Math.round(AnomalyClientState.intensity() * 100.0F),
                Math.round(AnomalyClientState.riftIntensity() * 100.0F), AnomalyPostEffectController.status());
    }

    private static int feedback(CommandSourceStack source, String key, Object... values) {
        source.sendSuccess(() -> Component.translatable(key, values), false);
        return 1;
    }
}
