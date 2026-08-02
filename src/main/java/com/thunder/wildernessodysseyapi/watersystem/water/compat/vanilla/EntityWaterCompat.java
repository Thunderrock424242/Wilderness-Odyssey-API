package com.thunder.wildernessodysseyapi.watersystem.water.compat.vanilla;

import com.thunder.wildernessodysseyapi.watersystem.water.api.WaterAccess;
import com.thunder.wildernessodysseyapi.watersystem.water.api.WaterServices;
import com.thunder.wildernessodysseyapi.watersystem.water.compat.CompatibilityLevel;
import com.thunder.wildernessodysseyapi.watersystem.water.compat.WaterCompatibilityAdapter;
import com.thunder.wildernessodysseyapi.watersystem.water.config.WaterSimulationConfig;
import net.minecraft.world.entity.Entity;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.tick.EntityTickEvent;

import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * Maintains one central custom-water state cache for vanilla entities.
 *
 * <p>The adapter detects footprint contact, full-body and eye submersion,
 * animated surface, depth, current, and transition timing. Narrow entity
 * mixins consume this state for vanilla swimming and breathing without
 * changing the identity of any registered fluid.</p>
 */
public final class EntityWaterCompat implements WaterCompatibilityAdapter {

    public static final String ID = "vanilla_entity_water";

    // Integrated clients tick client and server entities on separate threads.
    // Synchronizing map access also preserves weak-key cleanup for dead entities.
    private static final Map<Entity, EntityWaterState> STATES =
            Collections.synchronizedMap(new WeakHashMap<>());

    @Override
    public String id() {
        return ID;
    }

    @Override
    public CompatibilityLevel compatibilityLevel() {
        return CompatibilityLevel.INTEGRATED;
    }

    @Override
    public boolean isAvailable() {
        return true;
    }

    @Override
    public void initialize() {
        NeoForge.EVENT_BUS.register(this);
    }

    /** Updates cached state after vanilla has completed the entity's tick. */
    @SubscribeEvent
    public void onEntityTick(EntityTickEvent.Post event) {
        stateFor(event.getEntity());
    }

    /**
     * Returns state sampled no more than once for the entity's current game tick.
     */
    public static EntityWaterState stateFor(Entity entity) {
        EntityWaterState state = STATES.computeIfAbsent(entity, ignored -> new EntityWaterState());
        if (state.sampledAt(entity.level(), entity.level().getGameTime())) {
            return state;
        }
        if (!WaterSimulationConfig.entityWaterCompatEnabled()) {
            state.clear(entity);
            return state;
        }

        WaterAccess access = WaterServices.access();
        EntityWaterContactSampler.sample(access, entity, state);
        return state;
    }
}
