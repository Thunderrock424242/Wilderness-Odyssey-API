package com.thunder.wildernessodysseyapi.WorldGen.spawn;

import com.thunder.wildernessodysseyapi.Core.ModConstants;
import com.thunder.wildernessodysseyapi.WorldGen.structure.NBTStructurePlacer;
import com.thunder.wildernessodysseyapi.WorldGen.structure.StarterStructureSpawnGuard;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.AABB;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.level.LevelEvent;

import java.util.List;

/**
 * Places the starter bunker during world creation using vanilla structure templates rather than WorldEdit
 * or Starter Structure hooks.
 */
@EventBusSubscriber(modid = ModConstants.MOD_ID)
public final class SpawnBunkerPlacer {
    private static final ResourceLocation BUNKER_ID = ResourceLocation.fromNamespaceAndPath(ModConstants.MOD_ID, "bunker");
    private static final NBTStructurePlacer BUNKER_PLACER = new NBTStructurePlacer(BUNKER_ID);

    private SpawnBunkerPlacer() {
    }

    @SubscribeEvent
    public static void onCreateSpawn(LevelEvent.CreateSpawnPosition event) {
        if (!(event.getLevel() instanceof ServerLevel level)) {
            return;
        }
        if (!level.dimension().equals(Level.OVERWORLD)) {
            return;
        }

        BlockPos anchor = resolveAnchor(level);
        NBTStructurePlacer.PlacementResult result = placeBunker(level, anchor);
        if (result == null) {
            ModConstants.LOGGER.warn("Spawn bunker placement failed at {}; falling back to vanilla spawn selection.", anchor);
            return;
        }

        applySpawnData(level, result);
        event.setCanceled(true);
    }

    /**
     * Places the spawn bunker so that its leveling marker (or template origin) is anchored at the supplied position.
     */
    public static NBTStructurePlacer.PlacementResult placeBunker(ServerLevel level, BlockPos anchor) {
        return BUNKER_PLACER.placeAnchored(level, anchor);
    }

    private static BlockPos resolveAnchor(ServerLevel level) {
        BlockPos baseSpawn = level.getSharedSpawnPos();
        BlockPos surface = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                new BlockPos(baseSpawn.getX(), level.getMinBuildHeight(), baseSpawn.getZ()));
        return new BlockPos(surface.getX(), surface.getY(), surface.getZ());
    }

    private static void applySpawnData(ServerLevel level, NBTStructurePlacer.PlacementResult result) {
        List<BlockPos> cryoPositions = result.cryoPositions();
        if (!cryoPositions.isEmpty()) {
            CryoSpawnData data = CryoSpawnData.get(level);
            data.replaceAll(cryoPositions);
            PlayerSpawnHandler.setSpawnBlocks(cryoPositions);
            WorldSpawnHandler.refreshWorldSpawn(level);
        } else {
            PlayerSpawnHandler.setSpawnBlocks(List.of());
        }

        BlockPos spawnTarget = pickSpawnTarget(result);
        level.setDefaultSpawnPos(spawnTarget, 0.0F);

        StarterStructureSpawnGuard.registerSpawnDenyZone(level, result.bounds());
        clearGrassInBounds(level, result.bounds());

        ModConstants.LOGGER.info("Placed spawn bunker {} at {} with {} cryo tubes.", BUNKER_ID, result.origin(), cryoPositions.size());
    }

    private static BlockPos pickSpawnTarget(NBTStructurePlacer.PlacementResult result) {
        if (!result.cryoPositions().isEmpty()) {
            return result.cryoPositions().get(0);
        }
        return BlockPos.containing(result.bounds().getCenter());
    }

    private static void clearGrassInBounds(ServerLevel level, AABB bounds) {
        int minX = Mth.floor(bounds.minX);
        int minY = Mth.floor(bounds.minY);
        int minZ = Mth.floor(bounds.minZ);
        int maxX = Mth.ceil(bounds.maxX) - 1;
        int maxY = Mth.ceil(bounds.maxY) - 1;
        int maxZ = Mth.ceil(bounds.maxZ) - 1;

        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        for (int x = minX; x <= maxX; x++) {
            for (int y = minY; y <= maxY; y++) {
                for (int z = minZ; z <= maxZ; z++) {
                    cursor.set(x, y, z);
                    BlockState state = level.getBlockState(cursor);
                    if (isGrassBlock(state)) {
                        level.setBlock(cursor, Blocks.AIR.defaultBlockState(), 2);
                    }
                }
            }
        }
    }

    private static boolean isGrassBlock(BlockState state) {
        return state.is(Blocks.GRASS_BLOCK)
                || state.is(Blocks.DIRT)
                || state.is(Blocks.TALL_GRASS)
                || state.is(Blocks.FERN)
                || state.is(Blocks.LARGE_FERN);
    }
}
