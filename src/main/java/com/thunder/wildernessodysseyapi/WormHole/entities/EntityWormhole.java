package com.thunder.wildernessodysseyapi.WormHole.entities;

import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.Entity;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.Level;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ChunkMap;
import net.minecraft.world.level.chunk.LevelChunk;

import java.util.Random;

public class EntityWormhole extends Entity {
    public static final EntityType<EntityWormhole> WORMHOLE_ENTITY =
            EntityType.Builder.<EntityWormhole>of(EntityWormhole::new, MobCategory.MISC)
                    .sized(1.0F, 1.0F)
                    .build("wormhole");

    public EntityWormhole(EntityType<?> entityType, Level world) {
        super(entityType, world);
    }

    /**
     * @param builder
     */
    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {

    }

    @Override
    protected void readAdditionalSaveData(CompoundTag compound) {}

    @Override
    protected void addAdditionalSaveData(CompoundTag compound) {}

    @Override
    public void tick() {
        super.tick();
        if (!this.level.isClientSide) {
            // Randomly teleport nearby entities
            this.level.getEntities(this, this.getBoundingBox().inflate(5), e -> e != this)
                    .forEach(entity -> {
                        // Generate random coordinates for teleportation
                        Random random = this.random;
                        double x = random.nextInt(10000) - 5000;
                        double z = random.nextInt(10000) - 5000;
                        double y = this.level.getHeight(Level.ChunkType.WORLD_SURFACE, (int) x, (int) z);

                        // Load the destination chunks before teleportation
                        preloadChunks((ServerLevel) this.level, new BlockPos(x, y, z));

                        // Teleport the entity
                        entity.teleportTo(x, y, z);
                    });
        }
    }

    /**
     * Preload chunks around the destination to ensure a smooth transition.
     *
     * @param world The server level where the chunks are located.
     * @param pos   The position to load around.
     */
    private void preloadChunks(ServerLevel world, BlockPos pos) {
        int chunkRadius = 2; // Load a 5x5 chunk area (radius 2 around the target chunk)
        ChunkMap chunkManager = world.getChunkSource().chunkMap;

        for (int dx = -chunkRadius; dx <= chunkRadius; dx++) {
            for (int dz = -chunkRadius; dz <= chunkRadius; dz++) {
                int chunkX = (pos.getX() >> 4) + dx;
                int chunkZ = (pos.getZ() >> 4) + dz;

                // Force-load the chunk
                LevelChunk chunk = world.getChunk(chunkX, chunkZ);
                chunkManager.getVisibleChunkIfPresent(chunkX, chunkZ);
            }
        }
    }

    public static void registerEntityType() {
        GameRegistry.register(WORMHOLE_ENTITY, "wormhole");
    }

    public static void registerEntityRenderer() {
        // Client-only: Add rendering logic
    }
}
