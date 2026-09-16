package com.thunder.wildernessodysseyapi.tools.structureviewer.model;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

/**
 * Format-neutral preview data. Unresolved palette IDs and typed metadata stay intact without a
 * Minecraft registry. Additional readers can adapt to this model without changing the renderer.
 */
public record StructureData(String name, Path source, Position size, List<Block> blocks,
                            List<List<BlockState>> palettes, List<NbtValue> entities,
                            Map<String, NbtValue> metadata, List<String> diagnostics) {
    public StructureData {
        blocks = List.copyOf(blocks);
        palettes = palettes.stream().map(List::copyOf).toList();
        entities = List.copyOf(entities);
        metadata = Map.copyOf(metadata);
        diagnostics = List.copyOf(diagnostics);
    }

    /** Resolves the primary palette; broken references remain visible placeholders. */
    public BlockState state(Block block) {
        return !palettes.isEmpty() && block.paletteIndex >= 0 && block.paletteIndex < palettes.getFirst().size()
                ? palettes.getFirst().get(block.paletteIndex) : BlockState.MISSING;
    }

    /** Local block coordinate, also used for dimensions. */
    public record Position(int x, int y, int z) {
        /** Returns an adjacent coordinate without a Minecraft dependency. */
        public Position offset(int dx, int dy, int dz) { return new Position(x + dx, y + dy, z + dz); }
    }

    /** Stored block, original block-entity NBT, and extra entry metadata. */
    public record Block(Position position, int paletteIndex, NbtValue blockEntity,
                        Map<String, NbtValue> metadata) {
        public Block { metadata = Map.copyOf(metadata); }
    }

    /** Original ID and state properties; model resolution is a separate rendering responsibility. */
    public record BlockState(String id, Map<String, String> properties) {
        public static final BlockState MISSING = new BlockState("viewer:missing_palette_entry", Map.of());
        public BlockState { properties = Map.copyOf(properties); }
        /** Recognizes vanilla air variants, leaving structure_void inspectable. */
        public boolean isAir() {
            return id.equals("minecraft:air") || id.equals("minecraft:cave_air") || id.equals("minecraft:void_air");
        }
    }
}

