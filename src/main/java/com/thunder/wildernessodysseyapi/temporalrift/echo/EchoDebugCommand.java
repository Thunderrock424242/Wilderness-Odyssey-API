package com.thunder.wildernessodysseyapi.temporalrift.echo;

import com.mojang.brigadier.CommandDispatcher;
import com.thunder.wildernessodysseyapi.temporalrift.EchoBuildEchoSavedData;
import com.thunder.wildernessodysseyapi.temporalrift.registry.TemporalRiftDimensions;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import java.util.Locale;

/** Operator-only Echo diagnostics and temporary region overrides. Never mutates terrain. */
public final class EchoDebugCommand {
    private EchoDebugCommand() { }

    /** Joins the existing /wo root without replacing commands owned by other features. */
    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        var stability = Commands.literal("stability").executes(context -> show(context.getSource(), false));
        for (EchoStabilityLevel value : EchoStabilityLevel.values()) {
            stability.then(Commands.literal(value.name().toLowerCase(Locale.ROOT))
                    .executes(context -> override(context.getSource(), value)));
        }
        stability.then(Commands.literal("reset").executes(context -> override(context.getSource(), null)));
        dispatcher.register(Commands.literal("wo").then(Commands.literal("echo")
                .requires(source -> source.hasPermission(2))
                .then(stability)
                .then(Commands.literal("syncinfo").executes(context -> show(context.getSource(), true)))));
    }

    private static int override(CommandSourceStack source, EchoStabilityLevel value) {
        if (!com.thunder.wildernessodysseyapi.temporalrift.config.TemporalRiftConfig.ENABLE_ECHO_STABILITY_SYSTEM.get()) {
            source.sendFailure(Component.translatable("command.wildernessodysseyapi.echo.disabled"));
            return 0;
        }
        if (!source.getLevel().dimension().equals(TemporalRiftDimensions.THE_ECHO_KEY)) {
            source.sendFailure(Component.translatable("command.wildernessodysseyapi.echo.only_echo"));
            return 0;
        }
        EchoStabilityManager.setOverride(source.getLevel(), BlockPos.containing(source.getPosition()), value);
        source.sendSuccess(() -> Component.translatable("command.wildernessodysseyapi.echo.override",
                value == null ? "automatic" : value.name()), true);
        return show(source, false);
    }

    private static int show(CommandSourceStack source, boolean details) {
        EchoRegionState state = EchoStabilityManager.getStabilityAt(source.getLevel(), BlockPos.containing(source.getPosition()));
        source.sendSuccess(() -> Component.translatable("command.wildernessodysseyapi.echo.stability",
                state.stability().name(), Math.round(state.intensity() * 100), state.regionId(), state.overridden()), false);
        if (details) {
            source.sendSuccess(() -> Component.translatable("command.wildernessodysseyapi.echo.syncinfo",
                    source.getLevel().dimension().location().toString(),
                    state.nearestFracture() == null ? "none known" : state.nearestFracture().toShortString(),
                    state.fractureDistance() < 0 ? "n/a" : String.format(Locale.ROOT, "%.1f", state.fractureDistance()),
                    Math.round(state.fractureInfluence() * 100), EchoBuildEchoSavedData.get(source.getServer()).size()), false);
        }
        return 1;
    }
}
