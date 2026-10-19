package com.thunder.wildernessodysseyapi.watersystem.water.worldgen.river;

import com.thunder.wildernessodysseyapi.core.ModAttachments;
import com.thunder.wildernessodysseyapi.core.ModConstants;
import com.thunder.wildernessodysseyapi.watersystem.water.worldgen.GenerationWaterStateMapper;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.WorldGenRegion;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ProtoChunk;
import net.minecraft.world.level.levelgen.Heightmap;

/** Immutable terrain evidence captured at the existing CARVERS lifecycle boundary. */
public final class RiverTerrainCapture {
    public static final TagKey<Block> REPLACEABLE = TagKey.create(Registries.BLOCK,
            ResourceLocation.fromNamespaceAndPath(ModConstants.MOD_ID, "river_replaceable"));

    private RiverTerrainCapture() { }

    public static void capture(WorldGenRegion region, ChunkAccess chunk) {
        if (!RiverWorldgenConfig.enabled()) return;
        var settings = RiverWorldgenConfig.snapshot();
        if (!settings.enabled() || region.getLevel().dimension() != Level.OVERWORLD
                || !(chunk instanceof ProtoChunk) || chunk.isUpgrading()) return;
        int[] columns = new int[512];
        var pos = new BlockPos.MutableBlockPos();
        for (int z = 0; z < 16; z++) for (int x = 0; x < 16; x++) {
            int y = chunk.getHeight(Heightmap.Types.WORLD_SURFACE_WG, x, z);
            int cell = (x + z * 16) * 2;
            pos.set(chunk.getPos().getMinBlockX() + x, y, chunk.getPos().getMinBlockZ() + z);
            BlockState top = chunk.getBlockState(pos);
            boolean water = (top.is(Blocks.WATER) || GenerationWaterStateMapper.isWildernessWater(top))
                    && top.getFluidState().isSource();
            columns[cell] = (y & 0xFFFF) | (water ? 0x10000 : 0);
            int mask = 0;
            for (int depth = 0; depth < 16 && y - depth >= chunk.getMinBuildHeight(); depth++) {
                pos.setY(y - depth);
                if (natural(chunk.getBlockState(pos))) mask |= 1 << depth;
            }
            columns[cell + 1] = mask;
        }
        chunk.setData(ModAttachments.RIVER_GENERATION, RiverGenerationChunk.captured(settings.fingerprint(), columns));
        chunk.setUnsaved(true);
    }

    public static boolean natural(BlockState state) {
        return !state.hasBlockEntity() && !state.is(Blocks.BEDROCK)
                && state.getFluidState().isEmpty() && state.is(REPLACEABLE);
    }
}
