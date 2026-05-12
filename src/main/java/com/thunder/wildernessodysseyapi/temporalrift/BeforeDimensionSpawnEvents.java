package com.thunder.wildernessodysseyapi.temporalrift;

import com.thunder.wildernessodysseyapi.temporalrift.api.TheBeforeContentApi;
import com.thunder.wildernessodysseyapi.temporalrift.config.TemporalRiftConfig;
import com.thunder.wildernessodysseyapi.temporalrift.registry.TemporalRiftDimensions;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.ServerLevelAccessor;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.MobSpawnEvent;

import static com.thunder.wildernessodysseyapi.core.ModConstants.MOD_ID;

@EventBusSubscriber(modid = MOD_ID)
public final class BeforeDimensionSpawnEvents {
    private BeforeDimensionSpawnEvents() {
    }

    @SubscribeEvent
    public static void denyMobPlacement(MobSpawnEvent.SpawnPlacementCheck event) {
        if (!isBefore(event.getLevel())) {
            return;
        }

        ResourceLocation id = BuiltInRegistries.ENTITY_TYPE.getKey(event.getEntityType());
        if (!isMobAllowed(id)) {
            event.setResult(MobSpawnEvent.SpawnPlacementCheck.Result.FAIL);
        }
    }

    @SubscribeEvent
    public static void denyMobPosition(MobSpawnEvent.PositionCheck event) {
        if (!isBefore(event.getLevel())) {
            return;
        }

        ResourceLocation id = BuiltInRegistries.ENTITY_TYPE.getKey(event.getEntity().getType());
        if (!isMobAllowed(id)) {
            event.setResult(MobSpawnEvent.PositionCheck.Result.FAIL);
        }
    }

    private static boolean isBefore(ServerLevelAccessor level) {
        return level.getLevel().dimension().equals(TemporalRiftDimensions.THE_BEFORE_KEY);
    }

    private static boolean isMobAllowed(ResourceLocation id) {
        return TemporalRiftConfig.BEFORE_ALLOWED_MOBS.get().contains(id.toString()) || TheBeforeContentApi.isMobAllowed(id);
    }
}
