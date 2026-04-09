package com.thunder.wildernessodysseyapi.watersystem.water;

import com.thunder.wilderness.water.fluid.WildernessFluidRegistry;
import com.thunder.wilderness.water.particle.WildernessParticleRegistry;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;

@Mod(WaterPhysicsMod.MOD_ID)
public class WaterPhysicsMod {

    public static final String MOD_ID = "wilderness";

    public WaterPhysicsMod(IEventBus modEventBus) {
        WildernessFluidRegistry.register(modEventBus);
        WildernessParticleRegistry.register(modEventBus);
    }
}
