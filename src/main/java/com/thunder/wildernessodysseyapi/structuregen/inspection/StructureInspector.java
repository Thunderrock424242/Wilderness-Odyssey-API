package com.thunder.wildernessodysseyapi.structuregen.inspection;

import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.thunder.wildernessodysseyapi.structuregen.model.StructureBlock;
import com.thunder.wildernessodysseyapi.structuregen.model.StructureBlockState;
import com.thunder.wildernessodysseyapi.structuregen.model.StructureEntity;
import com.thunder.wildernessodysseyapi.structuregen.model.StructureModel;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.TagParser;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Computes loss-aware structure statistics without loading or modifying a Minecraft world.
 */
public final class StructureInspector {

    /** Builds a complete inspection report for an already-read canonical model. */
    public StructureInspectionReport inspect(Path file, StructureModel model) {
        Map<String, Long> blockCounts = model.blocks().stream()
                .collect(Collectors.groupingBy(
                        block -> block.state().blockId(),
                        TreeMap::new,
                        Collectors.counting()
                ));
        List<StructureInspectionReport.BlockFrequency> frequencies = blockCounts.entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed().thenComparing(Map.Entry::getKey))
                .map(entry -> new StructureInspectionReport.BlockFrequency(entry.getKey(), entry.getValue()))
                .toList();

        int explicitAir = (int) model.blocks().stream().filter(StructureBlock::isExplicitAir).count();
        int occupied = model.blocks().size() - explicitAir;
        double density = model.size().volume() == 0L ? 0.0D : (double) occupied / model.size().volume();
        int stateVariants = (int) model.blocks().stream()
                .map(block -> block.state().canonicalKey())
                .distinct()
                .count();

        List<List<StructureBlockState>> sourcePalettes = model.sourcePalettes().isEmpty()
                ? derivePrimaryPalette(model)
                : model.sourcePalettes();
        List<StructureInspectionReport.PaletteReport> palettes = paletteReports(sourcePalettes);
        List<StructureInspectionReport.VerticalLayer> vertical = verticalDistribution(model);
        Map<String, Long> blockEntityTypes = nbtIdCounts(
                model.blocks().stream().map(StructureBlock::blockEntitySnbt), "<missing-id>"
        );
        Map<String, Long> entityTypes = nbtIdCounts(
                model.entities().stream().map(StructureEntity::entityNbtSnbt), "<missing-id>"
        );
        Map<String, Long> categories = new StructureCategoryClassifier().classify(blockCounts);

        return new StructureInspectionReport(
                file.toAbsolutePath().normalize().toString(),
                model.name(),
                model.dataVersion(),
                model.size(),
                model.size().volume(),
                model.blocks().size(),
                explicitAir,
                occupied,
                density,
                sourcePalettes.size(),
                sourcePalettes.isEmpty() ? 0 : sourcePalettes.getFirst().size(),
                blockCounts.size(),
                stateVariants,
                blockEntityTypes.values().stream().mapToInt(Long::intValue).sum(),
                model.entities().size(),
                model.unsupportedFields(),
                frequencies,
                palettes,
                vertical,
                categories,
                blockEntityTypes,
                entityTypes
        );
    }

    private List<List<StructureBlockState>> derivePrimaryPalette(StructureModel model) {
        List<StructureBlockState> states = model.blocks().stream()
                .map(StructureBlock::state)
                .collect(Collectors.toMap(
                        StructureBlockState::canonicalKey,
                        Function.identity(),
                        (first, ignored) -> first,
                        TreeMap::new
                ))
                .values()
                .stream()
                .toList();
        return List.of(states);
    }

    private List<StructureInspectionReport.PaletteReport> paletteReports(
            List<List<StructureBlockState>> sourcePalettes
    ) {
        List<StructureInspectionReport.PaletteReport> reports = new ArrayList<>();
        for (int paletteIndex = 0; paletteIndex < sourcePalettes.size(); paletteIndex++) {
            List<StructureInspectionReport.PaletteEntry> entries = new ArrayList<>();
            List<StructureBlockState> palette = sourcePalettes.get(paletteIndex);
            for (int entryIndex = 0; entryIndex < palette.size(); entryIndex++) {
                StructureBlockState state = palette.get(entryIndex);
                entries.add(new StructureInspectionReport.PaletteEntry(
                        entryIndex, state.blockId(), state.properties()
                ));
            }
            reports.add(new StructureInspectionReport.PaletteReport(paletteIndex, entries));
        }
        return reports;
    }

    private List<StructureInspectionReport.VerticalLayer> verticalDistribution(StructureModel model) {
        Map<Integer, long[]> layers = new TreeMap<>();
        for (StructureBlock block : model.blocks()) {
            long[] counts = layers.computeIfAbsent(block.position().y(), ignored -> new long[2]);
            counts[0]++;
            if (!block.isExplicitAir()) {
                counts[1]++;
            }
        }
        List<StructureInspectionReport.VerticalLayer> result = new ArrayList<>();
        layers.forEach((y, counts) -> result.add(
                new StructureInspectionReport.VerticalLayer(y, counts[0], counts[1])
        ));
        return result;
    }

    private Map<String, Long> nbtIdCounts(Stream<String> snbtValues, String missingId) {
        Map<String, Long> counts = new TreeMap<>();
        snbtValues.forEach(snbt -> {
            if (snbt == null) {
                return;
            }
            String id = missingId;
            try {
                CompoundTag tag = TagParser.parseTag(snbt);
                if (!tag.getString("id").isBlank()) {
                    id = tag.getString("id");
                }
            } catch (CommandSyntaxException exception) {
                id = "<malformed-snbt>";
            }
            counts.merge(id, 1L, Long::sum);
        });
        return counts.entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed().thenComparing(Map.Entry::getKey))
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        Map.Entry::getValue,
                        (first, ignored) -> first,
                        LinkedHashMap::new
                ));
    }
}
