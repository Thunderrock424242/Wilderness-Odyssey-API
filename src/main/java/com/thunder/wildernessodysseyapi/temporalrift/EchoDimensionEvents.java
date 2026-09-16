package com.thunder.wildernessodysseyapi.temporalrift;

import com.thunder.wildernessodysseyapi.temporalrift.config.TemporalRiftConfig;
import com.thunder.wildernessodysseyapi.temporalrift.registry.TemporalRiftDimensions;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.chunk.LevelChunk;
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

        com.thunder.wildernessodysseyapi.temporalrift.echo.EchoDistortionManager.decorateNewChunk(level, chunk);
    }

    private static boolean isEcho(ServerLevelAccessor level) {
        return level.getLevel().dimension().equals(TemporalRiftDimensions.THE_ECHO_KEY);
    }

    private static boolean isMobAllowed(ResourceLocation id) {
        return id != null && TemporalRiftConfig.ECHO_ALLOWED_MOBS.get().contains(id.toString());
    }

}
