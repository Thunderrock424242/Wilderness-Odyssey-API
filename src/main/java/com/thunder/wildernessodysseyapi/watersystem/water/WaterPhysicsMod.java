package com.thunder.wildernessodysseyapi.watersystem.water;

import com.thunder.wildernessodysseyapi.core.ModConstants;
import com.thunder.wildernessodysseyapi.watersystem.water.fluid.WildernessFluidRegistry;
import com.thunder.wildernessodysseyapi.watersystem.water.particle.WildernessParticleRegistry;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;

@Mod(WaterPhysicsMod.MOD_ID)
public class WaterPhysicsMod {

    public static final String MOD_ID = ModConstants.MOD_ID;

    public WaterPhysicsMod(IEventBus modEventBus) {
        WildernessFluidRegistry.register(modEventBus);
        WildernessParticleRegistry.register(modEventBus);
    }
}
