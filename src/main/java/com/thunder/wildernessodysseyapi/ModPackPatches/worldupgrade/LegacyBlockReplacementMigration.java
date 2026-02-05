package com.thunder.wildernessodysseyapi.ModPackPatches.worldupgrade;

import com.thunder.wildernessodysseyapi.WorldGen.blocks.CryoTubeBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

import java.util.HashMap;
import java.util.Map;

/**
 * First migration step: replace known legacy block ids with current blocks.
 */
public final class LegacyBlockReplacementMigration implements WorldMigration {
    private static final Map<ResourceLocation, Block> REPLACEMENTS = buildReplacements();

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
        ChunkPos chunkPos = context.chunk().getPos();
        int minY = context.level().getMinBuildHeight();
        int maxY = context.level().getMaxBuildHeight();
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();

        for (int x = chunkPos.getMinBlockX(); x <= chunkPos.getMaxBlockX(); x++) {
            for (int z = chunkPos.getMinBlockZ(); z <= chunkPos.getMaxBlockZ(); z++) {
                for (int y = minY; y < maxY; y++) {
                    cursor.set(x, y, z);
                    BlockState state = context.level().getBlockState(cursor);
                    ResourceLocation key = BuiltInRegistries.BLOCK.getKey(state.getBlock());
                    Block replacement = REPLACEMENTS.get(key);
                    if (replacement == null || replacement == state.getBlock()) {
                        continue;
                    }
                    context.level().setBlock(cursor, replacement.defaultBlockState(), 2);
                }
            }
        }

        return true;
    }

    private static Map<ResourceLocation, Block> buildReplacements() {
        Map<ResourceLocation, Block> replacements = new HashMap<>();
        // Keep legacy aliases for old internal ids here.
        replacements.put(ResourceLocation.fromNamespaceAndPath("wildernessodyssey", "cryo_tube"), CryoTubeBlock.CRYO_TUBE.get());
        replacements.put(ResourceLocation.fromNamespaceAndPath("wildernessodysseyapi", "old_cryo_tube"), CryoTubeBlock.CRYO_TUBE.get());
        return replacements;
    }
}
