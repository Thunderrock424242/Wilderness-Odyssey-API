package com.thunder.wildernessodysseyapi.temporalrift;

import com.thunder.wildernessodysseyapi.temporalrift.config.TemporalRiftConfig;
import com.thunder.wildernessodysseyapi.temporalrift.registry.TemporalRiftDimensions;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.living.MobSpawnEvent;
import net.neoforged.neoforge.event.level.ChunkEvent;

import static com.thunder.wildernessodysseyapi.core.ModConstants.MOD_ID;

@EventBusSubscriber(modid = MOD_ID)
public final class EchoDimensionEvents {
    private EchoDimensionEvents() {
    }

    @SubscribeEvent
    public static void denyUnlistedMobPlacement(MobSpawnEvent.SpawnPlacementCheck event) {
        if (!isEcho(event.getLevel())) {
            return;
        }

        ResourceLocation id = BuiltInRegistries.ENTITY_TYPE.getKey(event.getEntityType());
        if (!isMobAllowed(id)) {
            event.setResult(MobSpawnEvent.SpawnPlacementCheck.Result.FAIL);
        }
    }

    @SubscribeEvent
    public static void denyUnlistedMobPosition(MobSpawnEvent.PositionCheck event) {
        if (!isEcho(event.getLevel())) {
            return;
        }

        ResourceLocation id = BuiltInRegistries.ENTITY_TYPE.getKey(event.getEntity().getType());
        if (!isMobAllowed(id)) {
            event.setResult(MobSpawnEvent.PositionCheck.Result.FAIL);
        }
    }

    @SubscribeEvent
    public static void removeVillagePeople(EntityJoinLevelEvent event) {
        if (event.getLevel().isClientSide() || !event.getLevel().dimension().equals(TemporalRiftDimensions.THE_ECHO_KEY)) {
            return;
        }

        EntityType<?> type = event.getEntity().getType();
        if (type == EntityType.VILLAGER || type == EntityType.WANDERING_TRADER || type == EntityType.ZOMBIE_VILLAGER) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void distortChunkOnLoad(ChunkEvent.Load event) {
        // Decay is generation decoration, not an ongoing rule that may rewrite player builds.
        if (!event.isNewChunk() || !TemporalRiftConfig.ENABLE_ECHO_CHUNK_DISTORTION.get()) {
            return;
        }
        if (!(event.getLevel() instanceof net.minecraft.server.level.ServerLevel level)
                || !level.dimension().equals(TemporalRiftDimensions.THE_ECHO_KEY)
                || !(event.getChunk() instanceof LevelChunk chunk)) {
            return;
        }

        openDoorsAndStripLeaves(level, chunk);
    }

    private static boolean isEcho(ServerLevelAccessor level) {
        return level.getLevel().dimension().equals(TemporalRiftDimensions.THE_ECHO_KEY);
    }

    private static boolean isMobAllowed(ResourceLocation id) {
        return id != null && TemporalRiftConfig.ECHO_ALLOWED_MOBS.get().contains(id.toString());
    }

    private static void openDoorsAndStripLeaves(net.minecraft.server.level.ServerLevel level, LevelChunk chunk) {
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        int minX = chunk.getPos().getMinBlockX();
        int minZ = chunk.getPos().getMinBlockZ();
        LevelChunkSection[] sections = chunk.getSections();

        // The palette check skips empty and irrelevant sections without 4,096 block reads apiece.
        for (int sectionIndex = 0; sectionIndex < sections.length; sectionIndex++) {
            LevelChunkSection section = sections[sectionIndex];
            if (section.hasOnlyAir() || !section.maybeHas(EchoDimensionEvents::isDistortionCandidate)) {
                continue;
            }
            int sectionMinY = chunk.getSectionYFromSectionIndex(sectionIndex) << 4;
            for (int localY = 0; localY < 16; localY++) {
                for (int localX = 0; localX < 16; localX++) {
                    for (int localZ = 0; localZ < 16; localZ++) {
                        BlockState state = section.getBlockState(localX, localY, localZ);
                        if (!isDistortionCandidate(state)) {
                            continue;
                        }
                        pos.set(minX + localX, sectionMinY + localY, minZ + localZ);
                        if (state.getBlock() instanceof DoorBlock && state.hasProperty(BlockStateProperties.OPEN)
                                && !state.getValue(BlockStateProperties.OPEN)) {
                            level.setBlock(pos, state.setValue(BlockStateProperties.OPEN, true), 2);
                        } else if (state.is(BlockTags.LEAVES) && shouldStripLeaf(level.getSeed(), pos)) {
                            level.setBlock(pos, Blocks.AIR.defaultBlockState(), 2);
                        }
                    }
                }
            }
        }
    }

    private static boolean isDistortionCandidate(BlockState state) {
        return state.getBlock() instanceof DoorBlock || state.is(BlockTags.LEAVES);
    }

    private static boolean shouldStripLeaf(long seed, BlockPos pos) {
        long cellX = Math.floorDiv(pos.getX(), 6);
        long cellY = Math.floorDiv(pos.getY(), 8);
        long cellZ = Math.floorDiv(pos.getZ(), 6);
        long hash = seed
                ^ (cellX * 0x9E3779B97F4A7C15L)
                ^ (cellY * 0xC2B2AE3D27D4EB4FL)
                ^ (cellZ * 0x165667B19E3779F9L);
        hash ^= hash >>> 33;
        hash *= 0xff51afd7ed558ccdL;
        hash ^= hash >>> 33;
        return Math.floorMod(hash, 100) < 7;
    }
}
