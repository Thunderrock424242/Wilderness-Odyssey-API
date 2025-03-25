package com.thunder.wildernessodysseyapi.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.neoforged.fml.ModList;

import java.util.Optional;

public class StructureInfoCommand {

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(
                Commands.literal("structureinfo")
                        .executes(StructureInfoCommand::execute)
        );
    }

    private static int execute(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        ServerLevel level = player.serverLevel();

        BlockPos targetPos = getTargetBlockPos(player);
        if (targetPos == null) {
            ctx.getSource().sendFailure(Component.literal("No valid block targeted."));
            return 0;
        }

        Optional<Holder.Reference<Structure>> structureHolder = level.registryAccess()
                .registryOrThrow(Registries.STRUCTURE)
                .holders()
                .filter(holder -> level.structureManager().getStructureAt(targetPos, (Structure) holder.value()).isValid())
                .findFirst();

        if (structureHolder.isEmpty()) {
            ctx.getSource().sendFailure(Component.literal("No structure found at this location."));
            return 0;
        }

        ResourceLocation structureId = structureHolder.get().key().location();

        String modName = ModList.get().getModContainerById(structureId.getNamespace())
                .map(container -> container.getModInfo().getDisplayName())
                .orElse("Unknown Mod");

        ctx.getSource().sendSuccess(() -> Component.literal(
                "Structure: " + structureId.getPath() + " | Mod: " + modName), false);

        return 1;
    }

    private static BlockPos getTargetBlockPos(ServerPlayer player) {
        var hitResult = player.pick(20.0D, 0.0F, false);
        return switch (hitResult.getType()) {
            case BLOCK, ENTITY -> BlockPos.containing(hitResult.getLocation());
            default -> player.blockPosition();
        };
    }
}