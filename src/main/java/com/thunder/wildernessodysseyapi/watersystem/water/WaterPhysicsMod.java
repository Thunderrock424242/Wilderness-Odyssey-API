package com.thunder.wildernessodysseyapi.watersystem.water;

import com.thunder.wildernessodysseyapi.core.ModConstants;
import com.thunder.wildernessodysseyapi.watersystem.water.fluid.WildernessFluidRegistry;
import com.thunder.wildernessodysseyapi.watersystem.water.particle.WildernessParticleRegistry;
import net.neoforged.bus.api.IEventBus;

public final class WaterPhysicsMod {

    public static final String MOD_ID = ModConstants.MOD_ID;

    private WaterPhysicsMod() {
    }

    public static void register(IEventBus modEventBus) {
        WildernessFluidRegistry.register(modEventBus);
        WildernessParticleRegistry.register(modEventBus);
    }
}
