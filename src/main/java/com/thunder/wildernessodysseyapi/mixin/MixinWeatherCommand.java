package com.thunder.wildernessodysseyapi.mixin;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.tree.LiteralCommandNode;
import com.thunder.wildernessodysseyapi.meteor.config.MeteorConfig;
import com.thunder.wildernessodysseyapi.meteor.event.MeteorImpactEvent;
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
 * Injects into vanilla WeatherCommand.register() to add:
 * <p>
 *   /weather meteor              — shower using config min/max count
 *   /weather meteor <count>      — shower with exact count (1–20)
 * <p>
 * The inject point is TAIL (end of the method), so all vanilla literals
 * (clear, rain, thunder) are already registered. We then pull the /weather
 * node directly from the dispatcher tree and add "meteor" as a proper child —
 * no merging tricks needed.
 */
@Mixin(WeatherCommand.class)
public class MixinWeatherCommand {

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
            int min = MeteorConfig.MIN_METEORS.get();
            int max = MeteorConfig.MAX_METEORS.get();
            resolvedCount = min + level.random.nextInt(Math.max(1, max - min + 1));
        } else {
            resolvedCount = count;
        }

        MeteorImpactEvent.spawnMeteorShower(level, resolvedCount);

        String countStr = resolvedCount == 1 ? "1 meteor" : resolvedCount + " meteors";
        source.sendSuccess(() -> Component.literal(
                "§6☄ Meteor shower triggered! (" + countStr + " incoming)"), true);

        return resolvedCount;
    }
}