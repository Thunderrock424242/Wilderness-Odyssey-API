package com.thunder.wildernessodysseyapi.tools.structureviewer.ui;

import com.thunder.wildernessodysseyapi.tools.structureviewer.model.NbtValue;
import com.thunder.wildernessodysseyapi.tools.structureviewer.model.StructureData;
import com.thunder.wildernessodysseyapi.tools.structureviewer.render.BlockMesh;
import java.util.TreeMap;

/** Formats source facts separately from Phase 1 rendering limitations. */
final class StructureDetails {
    private StructureDetails() {}

    static String summary(StructureData data) {
        long nonAir = data.blocks().stream().filter(block -> !data.state(block).isAir()).count();
        long blockEntities = data.blocks().stream().filter(block -> block.blockEntity() != null).count();
        return data.name() + "\n\nSource file\n" + data.source()
                + "\n\nDimensions (X / Y / Z)\n" + data.size().x() + " / " + data.size().y() + " / " + data.size().z()
                + "\n\nStored blocks: " + data.blocks().size() + "\nNon-air blocks: " + nonAir
                + "\nPrimary palette: " + data.palettes().getFirst().size() + "\nPalette variants: " + data.palettes().size()
                + "\nBlock-entity records: " + blockEntities + "\nEntities: " + data.entities().size()
                + "\n\nPhase 1 · approximate cube preview\nAll blocks use shaded cubes. Models, textures, transparency, "
                + "block shapes and entity rendering are planned for Phase 2. State properties are retained for inspection.";
    }

    static String inspection(StructureData data, int index) {
        if (index < 0 || index >= data.blocks().size()) return "Left-click a visible block to inspect it.";
        var block = data.blocks().get(index);
        var state = data.state(block);
        var p = block.position();
        StringBuilder text = new StringBuilder("Block ID\n").append(state.id())
                .append("\n\nPosition (X / Y / Z)\n").append(p.x()).append(" / ").append(p.y()).append(" / ").append(p.z())
                .append("\n\nPalette index: ").append(block.paletteIndex()).append("\nSource block index: ").append(index)
                .append("\n\nBlock state\n");
        if (state.properties().isEmpty()) text.append("(no properties)\n");
        else new TreeMap<>(state.properties()).forEach((key, value) -> text.append(key).append('=').append(value).append('\n'));
        text.append("\nBlock entity NBT\n").append(block.blockEntity() == null ? "(none)" : block.blockEntity().display());
        if (!block.metadata().isEmpty()) text.append("\n\nEntry metadata\n").append(new NbtValue(10, block.metadata()).display());
        return text.toString();
    }

    static String diagnostics(BlockMesh mesh) {
        StringBuilder text = new StringBuilder("Preview uses placeholder cubes for every block.\n")
                .append("No live Minecraft registry is available; block existence and allowed state values are not verified.\n\n")
                .append("Exposed faces: ").append(mesh.faces().size())
                .append("\nDuplicate visible coordinates: ").append(mesh.duplicatePositions())
                .append(" (last record shown)\n\nImport diagnostics\n");
        if (mesh.data().diagnostics().isEmpty()) text.append("No basic import problems found.\n");
        else mesh.data().diagnostics().forEach(message -> text.append("• ").append(message).append('\n'));
        text.append("\nPalette / appearance report\n");
        mesh.data().palettes().getFirst().stream().map(state -> state.id()).distinct().sorted().forEach(id ->
                text.append(id).append(id.startsWith("minecraft:") ? " — approximate cube\n" : " — magenta placeholder\n"));
        return text.toString();
    }
}

