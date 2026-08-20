package com.thunder.wildernessodysseyapi.mixin;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.tree.LiteralCommandNode;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.thunder.wildernessodysseyapi.meteor.api.MeteorSiteSource;
import com.thunder.wildernessodysseyapi.meteor.config.MeteorConfig;
import com.thunder.wildernessodysseyapi.meteor.event.MeteorImpactEvent;
import com.thunder.wildernessodysseyapi.weather.integration.VanillaWeatherCommandAdapter;
import com.thunder.wildernessodysseyapi.weather.simulation.WeatherAuthority;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.commands.WeatherCommand;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Bridges vanilla clear, rain, and thunder into the localized weather authority.
 *
 * <p>Vanilla keeps ownership of permissions, global state, duration selection,
 * and command feedback. This mixin mirrors its final weather parameters into
 * Wilderness atmospheric cells for that same duration.</p>
 *
 * <p>It also injects into vanilla WeatherCommand.register() to add:</p>
 *
 *   /weather meteor              — shower using config min/max count
 *   /weather meteor <count>      — shower with exact count (1–20)
 *
 * The inject point is TAIL (end of the method), so all vanilla literals
 * (clear, rain, thunder) are already registered. We then pull the /weather
 * node directly from the dispatcher tree and add "meteor" as a proper child —
 * no merging tricks needed.
 */
@Mixin(WeatherCommand.class)
public class MixinWeatherCommand {

    @WrapOperation(
            method = {"setClear", "setRain", "setThunder"},
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/server/level/ServerLevel;setWeatherParameters(IIZZ)V"
            )
    )
    private static void wilderness$bridgeVanillaWeather(
            ServerLevel level,
            int clearDuration,
            int weatherDuration,
            boolean raining,
            boolean thundering,
            Operation<Void> original
    ) {
        // Vanilla commits its global state first; the localized authority then
        // mirrors exactly the state and duration selected by the command.
        original.call(level, clearDuration, weatherDuration, raining, thundering);
        int duration = raining ? weatherDuration : clearDuration;
        WeatherAuthority.get().applyVanillaCommandWeather(
                level,
                VanillaWeatherCommandAdapter.fromParameters(raining, thundering),
                duration
        );
    }

    @Inject(method = "register", at = @At("TAIL"))
    private static void wilderness$addMeteorSubcommand(
            CommandDispatcher<CommandSourceStack> dispatcher,
            CallbackInfo ci
    ) {
        // Grab the already-registered /weather node from the dispatcher tree
        LiteralCommandNode<CommandSourceStack> weatherNode =
                (LiteralCommandNode<CommandSourceStack>)
                        dispatcher.getRoot().getChild("weather");

        if (weatherNode == null) return; // safety: vanilla didn't register it somehow

        // Build the meteor subtree and add it directly as a child
        weatherNode.addChild(
                Commands.literal("meteor")
                        .requires(source -> source.hasPermission(2))

                        // /weather meteor  (no count arg — uses config range)
                        .executes(ctx -> triggerMeteorShower(ctx.getSource(), -1))

                        // /weather meteor <count>
                        .then(Commands.argument("count", IntegerArgumentType.integer(1, 20))
                                .executes(ctx -> triggerMeteorShower(
                                        ctx.getSource(),
                                        IntegerArgumentType.getInteger(ctx, "count")
                                ))
                        )
                        .build()
        );
    }

    private static int triggerMeteorShower(CommandSourceStack source, int count) {
        ServerLevel level = source.getLevel();

        if (!level.dimension().equals(Level.OVERWORLD)) {
            source.sendFailure(Component.literal(
                    "Meteor showers can only be triggered in the Overworld."));
            return 0;
        }

        if (level.players().isEmpty()) {
            source.sendFailure(Component.literal(
                    "No players are in the Overworld to target."));
            return 0;
        }

        int resolvedCount;
        if (count == -1) {
            resolvedCount = MeteorConfig.meteorCountRange().randomValue(level.random);
        } else {
            resolvedCount = count;
        }

        int acceptedCount = MeteorImpactEvent.requestMeteorShower(
                level,
                resolvedCount,
                MeteorSiteSource.COMMAND
        );
        if (acceptedCount == 0) {
            source.sendFailure(Component.literal(
                    "The meteor queue is full; wait for the current shower to clear."));
            return 0;
        }

        String countStr = acceptedCount == 1 ? "1 meteor" : acceptedCount + " meteors";
        source.sendSuccess(() -> Component.literal(
                "§6☄ Meteor shower triggered! (" + countStr + " incoming)"), true);

        return acceptedCount;
    }
}
