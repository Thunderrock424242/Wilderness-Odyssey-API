package com.thunder.wildernessodysseyapi.worldgen.spawn;

import com.thunder.wildernessodysseyapi.worldgen.structure.LargeStructurePlacementOptimizer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.TicketType;
import net.minecraft.world.level.ChunkPos;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * Owns temporary chunk availability for the selected starter bunker during world creation.
 *
 * <p>Island sampling generates columns but its transient Minecraft tickets can expire while
 * later columns are prepared. Explicit, non-ticking region tickets retain only the bounded
 * template footprint until construction finishes; other structure placement remains loaded-only.
 * No forced-chunk state is persisted, and every acquired ticket is released on failure too.</p>
 */
final class StarterBunkerChunkPreparation {
    private static final TicketType<UUID> CONSTRUCTION_TICKET = TicketType.create(
            "wildernessodysseyapi_starter_bunker", UUID::compareTo);

    private StarterBunkerChunkPreparation() { }

    static <T> T withChunks(ServerLevel level, BlockPos origin, Vec3i size, Supplier<T> construction) {
        if (!level.getServer().isSameThread() || level.getServer().isStopped()) {
            throw new IllegalStateException("Starter chunk preparation requires the active server thread");
        }
        long count = LargeStructurePlacementOptimizer.countPlacementChunks(origin, size);
        if (!LargeStructurePlacementOptimizer.isWithinTemplateBudget(size)
                || count <= 0 || count > LargeStructurePlacementOptimizer.MAX_PLACEMENT_CHUNKS) {
            return null;
        }

        ChunkPos first = new ChunkPos(origin);
        ChunkPos last = new ChunkPos(origin.offset(size.getX() - 1, 0, size.getZ() - 1));
        UUID owner = UUID.randomUUID();
        List<ChunkPos> retained = new ArrayList<>((int) count);
        var chunks = level.getChunkSource();
        try {
            // Retain the entire admitted footprint before blocking generation of its first chunk.
            // Each call owns a distinct ticket value, so nested scopes cannot release each other.
            for (int x = first.x; x <= last.x; x++) {
                for (int z = first.z; z <= last.z; z++) {
                    ChunkPos position = new ChunkPos(x, z);
                    chunks.addRegionTicket(CONSTRUCTION_TICKET, position, 0, owner);
                    retained.add(position);
                }
            }
            for (ChunkPos position : retained) level.getChunk(position.x, position.z);
            return construction.get();
        } finally {
            for (ChunkPos position : retained) {
                chunks.removeRegionTicket(CONSTRUCTION_TICKET, position, 0, owner);
            }
        }
    }
}
