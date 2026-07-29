package com.thunder.wildernessodysseyapi.watersystem.water.compat.create;

import com.thunder.wildernessodysseyapi.watersystem.water.compat.CompatibilityLevel;
import com.thunder.wildernessodysseyapi.watersystem.water.compat.WaterCompatibilityAdapter;
import net.neoforged.fml.ModList;

/**
 * Enables Create-local recognition of the namespaced Wilderness water fluid.
 *
 * <p>Create 6.0.10 exposes no public transaction hook for open-ended pipe
 * world writes. Those mutations are therefore protected by the general
 * NeoForge capability and projected-block reconciliation adapter, while this
 * adapter only extends Create's own water predicate.</p>
 */
public final class CreateWaterCompatibilityAdapter implements WaterCompatibilityAdapter {

    @Override
    public String id() {
        return "create_water";
    }

    @Override
    public CompatibilityLevel compatibilityLevel() {
        return CompatibilityLevel.INTEGRATED;
    }

    @Override
    public boolean isAvailable() {
        return ModList.get().isLoaded("create");
    }

    @Override
    public void initialize() {
        CreateWaterCompatibilityState.activate();
    }
}
