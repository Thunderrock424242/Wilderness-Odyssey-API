package com.thunder.wildernessodysseyapi.WormHole.entities;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ChunkMap;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public class EntityWormhole extends Entity {
    public static final DeferredRegister<EntityType<?>> ENTITY_TYPES = DeferredRegister.create(Registries.ENTITY_TYPES, "wormholemod");

    public static final DeferredHolder<EntityType<?>, EntityType<EntityWormhole>> WORMHOLE_ENTITY = ENTITY_TYPES.register("wormhole",
            () -> EntityType.Builder.<EntityWormhole>of(EntityWormhole::new, MobCategory.MISC)
                    .sized(1.0F, 1.0F)
                    .build("wormhole")
    );

    public EntityWormhole(DeferredHolder<EntityType<?>, EntityType<EntityWormhole>> entityType, Level level) {
        super(entityType, level);
    }

    @Override
    protected void defineSynchedData() {}

    @Override
    protected void readAdditionalSaveData(CompoundTag compound) {}

    @Override
    protected void addAdditionalSaveData(CompoundTag compound) {}

    /**
     * @param builder
     */
    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {

    }

    @Override
    public void tick() {
        super.tick();
        if (!this.level.isClientSide) {
            this.level.getEntities(this, this.getBoundingBox().inflate(5), e -> e != this)
                    .forEach(entity -> {
                        RandomSource random = this.level.random;
                        double x = random.nextInt(10000) - 5000;
                        double z = random.nextInt(10000) - 5000;
                        double y = this.level.getHeight(Heightmap.Types.MOTION_BLOCKING, (int) x, (int) z);

                        preloadChunks((ServerLevel) this.level, new BlockPos((int) x, (int) y, (int) z));

                        entity.teleportTo(x, y, z);
                    });
        }
    }

    private void preloadChunks(ServerLevel world, BlockPos pos) {
        int chunkRadius = 2;
        ChunkMap chunkManager = world.getChunkSource().chunkMap;

        for (int dx = -chunkRadius; dx <= chunkRadius; dx++) {
            for (int dz = -chunkRadius; dz <= chunkRadius; dz++) {
                int chunkX = (pos.getX() >> 4) + dx;
                int chunkZ = (pos.getZ() >> 4) + dz;
                long chunkPosLong = ChunkPos.asLong(chunkX, chunkZ);
                chunkManager.getVisibleChunkIfPresent(chunkPosLong);
            }
        }
    }

    public static void register(IEventBus eventBus) {
        ENTITY_TYPES.register(eventBus);
    }
}
