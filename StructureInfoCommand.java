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
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.fml.ModList;

import java.util.Optional;
import java.util.stream.Collectors;

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
        BlockPos pos = getTargetBlockPos(player);

        StringBuilder output = new StringBuilder();

        // Structures
        var structureRegistry = level.registryAccess().registryOrThrow(Registries.STRUCTURE);
        var structures = structureRegistry.holders()
                .filter(holder -> level.structureManager().getStructureAt(pos, holder.value()).isValid())
                .map(holder -> holder.unwrapKey().map(ResourceKey::location))
                .flatMap(Optional::stream)
                .map(StructureInfoCommand::formatEntry)
                .collect(Collectors.toList());

        // Features
        var featureRegistry = level.registryAccess().registryOrThrow(Registries.CONFIGURED_FEATURE);
        var biome = level.getBiome(pos).value();

        var features = biome.getGenerationSettings().features().stream()
                .flatMap(holders -> holders.stream()) // Flatten each decoration step
                .flatMap(holder -> holder.unwrapKey().stream()) // Safe unwrap key
                .map(ResourceKey::location)
                .map(StructureInfoCommand::formatEntry) // Add mod display names
                .distinct()
                .toList();

        // Points of Interest (POIs)
        var poiRegistry = level.registryAccess().registryOrThrow(Registries.POINT_OF_INTEREST_TYPE);
        var pois = poiRegistry.stream()
                .filter(poi -> level.getPoiManager().existsAtPosition(poi, pos))
                .map(poiRegistry::getResourceKey)
                .flatMap(Optional::stream)
                .map(key -> formatEntry(key.location()))
                .toList();

        // Output construction
        if (structures.isEmpty() && features.isEmpty() && pois.isEmpty()) {
            output.append("No structures, features, or POIs found at this location.");
        } else {
            output.append("Structure Info at your location:");

            if (!structures.isEmpty()) {
                output.append("\n§eStructures:");
                structures.forEach(s -> output.append("\n - §6").append(s));
            }
            if (!features.isEmpty()) {
                output.append("\n§aFeatures:");
                features.forEach(f -> output.append("\n - §2").append(f));
            }
            if (!pois.isEmpty()) {
                output.append("\n§bPoints of Interest:");
                pois.forEach(p -> output.append("\n - §3").append(p));
            }
        }

        ctx.getSource().sendSuccess(() -> Component.literal(output.toString()), false);
        return 1;
    }

    private static String formatEntry(ResourceLocation location) {
        String modName = ModList.get().getModContainerById(location.getNamespace())
                .map(container -> container.getModInfo().getDisplayName())
                .orElse("Unknown Mod");
        return location.getPath() + " §7[" + modName + "§7]";
    }

    private static BlockPos getTargetBlockPos(ServerPlayer player) {
        var hitResult = player.pick(20.0D, 0.0F, false);
        return switch (hitResult.getType()) {
            case BLOCK, ENTITY -> BlockPos.containing(hitResult.getLocation());
            default -> player.blockPosition();
        };
    }
}
