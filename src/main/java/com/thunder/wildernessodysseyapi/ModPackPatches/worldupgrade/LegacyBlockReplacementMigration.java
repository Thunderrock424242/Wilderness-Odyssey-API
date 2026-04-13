package com.thunder.wildernessodysseyapi.ModPackPatches.worldupgrade;

import com.thunder.wildernessodysseyapi.worldgen.blocks.CryoTubeBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/**
 * First migration step: replace known legacy block ids with current blocks.
 */
public final class LegacyBlockReplacementMigration implements WorldMigration {

    // Resolve the blocks ONCE into memory to prevent doing 98,000 string lookups per chunk
    private static final Block LEGACY_CRYO_1 = resolveLegacyBlock("wildernessodyssey", "cryo_tube");
    private static final Block LEGACY_CRYO_2 = resolveLegacyBlock("wildernessodysseyapi", "old_cryo_tube");

    private static Block resolveLegacyBlock(String namespace, String path) {
        ResourceLocation id = ResourceLocation.fromNamespaceAndPath(namespace, path);
        return BuiltInRegistries.BLOCK.getOptional(id)
                .filter(block -> block != Blocks.AIR)
                .orElse(null);
    }

    @Override
    public String id() {
        return "legacy_block_replacements";
    }

    @Override
    public int fromVersion() {
        return 0;
    }

    @Override
    public int toVersion() {
        return 1;
    }

    @Override
    public boolean apply(MigrationContext context) {
        // If the blocks didn't resolve (e.g. they aren't in the registry), skip migration
        if (LEGACY_CRYO_1 == null && LEGACY_CRYO_2 == null) return true;

        ChunkPos chunkPos = context.chunk().getPos();
        int minY = context.level().getMinBuildHeight();
        int maxY = context.level().getMaxBuildHeight();
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();

        Block newCryo = CryoTubeBlock.CRYO_TUBE.get();
        BlockState newCryoState = newCryo.defaultBlockState();

        // Optimized Loop: Y -> Z -> X is the fastest iteration order for Minecraft chunks
        for (int y = minY; y < maxY; y++) {

            // OPTIMIZATION: Skip completely empty 16x16x16 chunk sections (Sky/Caves)
            int sectionIndex = context.chunk().getSectionIndex(y);
            if (context.chunk().getSection(sectionIndex).hasOnlyAir()) {
                y += 15; // Fast-forward to the end of this empty section
                continue;
            }

            for (int z = chunkPos.getMinBlockZ(); z <= chunkPos.getMaxBlockZ(); z++) {
                for (int x = chunkPos.getMinBlockX(); x <= chunkPos.getMaxBlockX(); x++) {
                    cursor.set(x, y, z);
                    BlockState state = context.level().getBlockState(cursor);
                    Block block = state.getBlock();

                    // Lightning fast memory reference check instead of Map/Registry string lookup
                    if (block == LEGACY_CRYO_1 || block == LEGACY_CRYO_2) {
                        context.level().setBlock(cursor, newCryoState, 2);
                    }
                }
            }
        }

        return true;
    }
}
