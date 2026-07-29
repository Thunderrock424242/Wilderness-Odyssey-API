package com.thunder.wildernessodysseyapi.watersystem.water.compat.neoforge;

import com.thunder.wildernessodysseyapi.watersystem.water.compat.CompatibilityLevel;
import com.thunder.wildernessodysseyapi.watersystem.water.compat.WaterCompatibilityAdapter;
import com.thunder.wildernessodysseyapi.watersystem.water.config.WaterSimulationConfig;
import com.thunder.wildernessodysseyapi.watersystem.water.config.WildernessWaterRules;
import com.thunder.wildernessodysseyapi.watersystem.water.fluid.WildernessFluidRegistry;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;

import java.util.Objects;

/**
 * Registers the standard NeoForge fluid transaction boundary for Wilderness water.
 *
 * <p>Capability discovery remains present for the lifetime of the process, but
 * its provider returns {@code null} while compatibility is disabled. This lets
 * packs roll back machine integration without changing canonical simulation.</p>
 */
public final class NeoForgeFluidHandlerAdapter implements WaterCompatibilityAdapter {

    private final IEventBus modEventBus;

    /** Creates the adapter with the owning mod's lifecycle event bus. */
    public NeoForgeFluidHandlerAdapter(IEventBus modEventBus) {
        this.modEventBus = Objects.requireNonNull(modEventBus, "modEventBus");
    }

    @Override
    public String id() {
        return "neoforge_fluid_handler";
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
        modEventBus.addListener(this::registerCapabilities);
        WorldFluidMutationReconciler.activate();
    }

    // NeoForge asks for block capability providers after deferred registries
    // exist, so the namespaced liquid block is safe to reference here.
    private void registerCapabilities(RegisterCapabilitiesEvent event) {
        event.registerBlock(
                Capabilities.FluidHandler.BLOCK,
                (level, position, state, blockEntity, side) -> {
                    if (!(level instanceof ServerLevel serverLevel)
                            || !WaterSimulationConfig.fluidHandlerCompatEnabled()
                            || !WildernessWaterRules.isEnabled(serverLevel)) {
                        return null;
                    }
                    return new AuthorityWaterFluidHandler(serverLevel, position);
                },
                WildernessFluidRegistry.WILDERNESS_WATER_BLOCK.get()
        );
    }
}
