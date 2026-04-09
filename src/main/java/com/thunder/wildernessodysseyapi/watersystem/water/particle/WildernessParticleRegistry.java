package com.thunder.wildernessodysseyapi.watersystem.water.particle;

import com.thunder.wilderness.water.WaterPhysicsMod;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * WildernessParticleRegistry
 *
 * Registers custom particle types for the water physics system.
 * Currently registers:
 *   RIPPLE — a flat expanding ring drawn on the water surface
 */
public class WildernessParticleRegistry {

    public static final DeferredRegister<net.minecraft.core.particles.ParticleType<?>> PARTICLES =
        DeferredRegister.create(BuiltInRegistries.PARTICLE_TYPE, WaterPhysicsMod.MOD_ID);

    public static final DeferredHolder<net.minecraft.core.particles.ParticleType<?>, SimpleParticleType> RIPPLE =
        PARTICLES.register("ripple", () -> new SimpleParticleType(false));

    public static void register(IEventBus modEventBus) {
        PARTICLES.register(modEventBus);
    }
}
