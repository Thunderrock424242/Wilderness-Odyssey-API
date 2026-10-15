package com.thunder.wildernessodysseyapi.watersystem.water.compat.vanilla;

import com.thunder.wildernessodysseyapi.watersystem.water.api.WaterAccess;
import com.thunder.wildernessodysseyapi.watersystem.water.api.WaterServices;
import com.thunder.wildernessodysseyapi.watersystem.water.compat.CompatibilityLevel;
import com.thunder.wildernessodysseyapi.watersystem.water.compat.WaterCompatibilityAdapter;
import com.thunder.wildernessodysseyapi.watersystem.water.config.WaterSimulationConfig;
import com.thunder.wildernessodysseyapi.watersystem.water.config.WildernessWaterRules;
import com.thunder.wildernessodysseyapi.watersystem.water.volume.LoadedWaterReadAccess;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.tick.EntityTickEvent;
import net.neoforged.neoforge.event.entity.EntityLeaveLevelEvent;
import net.neoforged.neoforge.event.level.LevelEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;

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
    // No sampler or lifecycle handler mutates this fallback. Its unknown contact
    // leaves the original vanilla fluid decision intact during generation.
    private static final EntityWaterState UNAVAILABLE = new EntityWaterState();

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

    /** Releases entity state when a chunk unloads or an entity leaves its level. */
    @SubscribeEvent
    public void onEntityLeave(EntityLeaveLevelEvent event) {
        STATES.remove(event.getEntity());
    }

    /** Clears both client and server entries belonging to the unloading level. */
    @SubscribeEvent
    public void onLevelUnload(LevelEvent.Unload event) {
        synchronized (STATES) {
            STATES.keySet().removeIf(entity -> entity.level() == event.getLevel());
        }
    }

    /** Drops server entries even if shutdown did not complete every level callback. */
    @SubscribeEvent
    public void onServerStopped(ServerStoppedEvent event) {
        synchronized (STATES) {
            STATES.keySet().removeIf(entity -> entity.level() instanceof ServerLevel level
                    && level.getServer() == event.getServer());
        }
    }

    /**
     * Returns state sampled no more than once for the entity's current game tick.
     * Unadded/removed entities and server generation workers retain vanilla
     * behavior without reading or populating the runtime cache.
     */
    public static EntityWaterState stateFor(Entity entity) {
        Level level = entity.level();
        if (!LoadedWaterReadAccess.canRead(level) || entity.isRemoved() || !entity.isAddedToLevel()) {
            return UNAVAILABLE;
        }
        EntityWaterState state = STATES.computeIfAbsent(entity, ignored -> new EntityWaterState());
        if (state.sampledAt(level, level.getGameTime())) {
            return state;
        }
        if (!WildernessWaterRules.isEnabled(level)
                || !WaterSimulationConfig.entityWaterCompatEnabled()) {
            state.clear(entity);
            return state;
        }

        WaterAccess access = WaterServices.access();
        EntityWaterContactSampler.sample(access, entity, state);
        return state;
    }
}
