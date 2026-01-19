package com.thunder.wildernessodysseyapi.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.village.poi.PoiType;
import net.minecraft.world.level.biome.BiomeGenerationSettings;
import net.minecraft.world.level.levelgen.feature.ConfiguredFeature;
import net.minecraft.world.level.levelgen.placement.PlacedFeature;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.neoforged.fml.ModList;

import java.util.*;
import java.util.stream.Collectors;
import javax.swing.*;

/****
 * StructureInfoCommand for the Wilderness Odyssey API mod.
 */
public class StructureInfoCommand {

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(
                Commands.literal("structureinfo")
                        .executes(StructureInfoCommand::execute)
        );
    }

    private static int execute(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        ServerLevel level   = player.serverLevel();
        BlockPos   pos      = getTargetBlockPos(player);

        StringBuilder output = new StringBuilder();

        // Structures
        Registry<Structure> structReg = level.registryAccess()
                .registryOrThrow(Registries.STRUCTURE);
        List<String> structures = structReg.entrySet().stream()
                .filter(entry -> level.structureManager().getStructureAt(pos, entry.getValue()).isValid())
                .map(entry -> formatEntry(entry.getKey().location()))
                .collect(Collectors.toList());

        // --- FEATURES (from the biome at that position) ---Add commentMore actions
        Registry<ConfiguredFeature<?, ?>> featReg = level.registryAccess()
                .registryOrThrow(Registries.CONFIGURED_FEATURE);

        // Get the biome’s generation settings
        BiomeGenerationSettings gen = level.getBiome(pos).value().getGenerationSettings();

        List<String> features = gen.features().stream()                                 // Stream<HolderSet<PlacedFeature>>
                .flatMap((HolderSet<PlacedFeature> holderSet) -> holderSet.stream())        // Stream<Holder<PlacedFeature>>
                .map(Holder::value)                                                         // Stream<PlacedFeature>
                .map(pf -> pf.feature().unwrapKey()                                         // for each PlacedFeature, get its ConfiguredFeature key
                        .map(key -> formatEntry(key.location()))
                        .orElse(null)
                )
                .filter(Objects::nonNull)
                .distinct()
                .collect(Collectors.toList());

        // --- POINTS OF INTEREST ---Add commentMore actions
        Registry<PoiType> poiReg = level.registryAccess()
                .registryOrThrow(Registries.POINT_OF_INTEREST_TYPE);
        List<String> pois = poiReg.entrySet().stream()
                .filter(entry -> level.getPoiManager().existsAtPosition(entry.getKey(), pos))
                .map(entry -> formatEntry(entry.getKey().location()))
                .collect(Collectors.toList());

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

        showInfoWindow(structures, features, pois);
        ctx.getSource().sendSuccess(() -> Component.literal(output.toString()), false);
        return 1;
    }

    private static void showInfoWindow(List<String> structures, List<String> features, List<String> pois) {
        SwingUtilities.invokeLater(() -> {
            JFrame frame = new JFrame("Structure Info");
            frame.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
            frame.setSize(400, 600);

            JTabbedPane tabs = new JTabbedPane();
            tabs.addTab("Structures", new JScrollPane(new JList<>(structures.toArray(new String[0]))));
            tabs.addTab("Features", new JScrollPane(new JList<>(features.toArray(new String[0]))));
            tabs.addTab("POIs", new JScrollPane(new JList<>(pois.toArray(new String[0]))));

            frame.add(tabs);
            frame.setVisible(true);
        });
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
