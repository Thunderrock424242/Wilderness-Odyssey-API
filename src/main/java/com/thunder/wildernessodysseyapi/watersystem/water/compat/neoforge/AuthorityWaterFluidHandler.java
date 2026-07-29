package com.thunder.wildernessodysseyapi.watersystem.water.compat.neoforge;

import com.thunder.wildernessodysseyapi.core.ModConstants;
import com.thunder.wildernessodysseyapi.watersystem.water.api.WaterAccess;
import com.thunder.wildernessodysseyapi.watersystem.water.api.WaterInteractionResult;
import com.thunder.wildernessodysseyapi.watersystem.water.api.WaterServices;
import com.thunder.wildernessodysseyapi.watersystem.water.config.WaterSimulationConfig;
import com.thunder.wildernessodysseyapi.watersystem.water.config.WildernessWaterRules;
import com.thunder.wildernessodysseyapi.watersystem.water.fluid.WildernessFluidRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.FluidTags;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;

import java.util.Objects;
import java.util.function.BooleanSupplier;

/**
 * Presents one authority-owned water cell as a NeoForge fluid tank.
 *
 * <p>The handler does not store fluid. Every simulation and execution delegates
 * to {@link WaterAccess}, keeping machine transfers atomic with canonical
 * volume and its projected block. The returned stack retains the namespaced
 * Wilderness fluid identity; cross-mod water behavior is supplied by fluid
 * tags and focused adapters rather than a global vanilla-fluid identity lie.</p>
 */
public final class AuthorityWaterFluidHandler implements IFluidHandler {

    private final ServerLevel level;
    private final BlockPos position;
    private final WaterAccess waterAccess;
    private final BooleanSupplier compatibilityEnabled;

    /** Creates the runtime handler used by NeoForge's block capability. */
    public AuthorityWaterFluidHandler(ServerLevel level, BlockPos position) {
        this(level, position, WaterServices.access(), WaterSimulationConfig::fluidHandlerCompatEnabled);
    }

    AuthorityWaterFluidHandler(
            ServerLevel level,
            BlockPos position,
            WaterAccess waterAccess,
            BooleanSupplier compatibilityEnabled
    ) {
        this.level = Objects.requireNonNull(level, "level");
        this.position = Objects.requireNonNull(position, "position").immutable();
        this.waterAccess = Objects.requireNonNull(waterAccess, "waterAccess");
        this.compatibilityEnabled = Objects.requireNonNull(compatibilityEnabled, "compatibilityEnabled");
    }

    @Override
    public int getTanks() {
        return 1;
    }

    @Override
    public FluidStack getFluidInTank(int tank) {
        if (tank != 0 || !canTransact()) {
            return FluidStack.EMPTY;
        }
        int amount = WaterUnitConversions.toMilliBuckets(currentUnits());
        return amount <= 0
                ? FluidStack.EMPTY
                : new FluidStack(WildernessFluidRegistry.WILDERNESS_WATER.get(), amount);
    }

    @Override
    public int getTankCapacity(int tank) {
        return tank == 0 ? WaterUnitConversions.MILLIBUCKETS_PER_BLOCK : 0;
    }

    @Override
    public boolean isFluidValid(int tank, FluidStack stack) {
        return tank == 0 && isPlainTaggedWater(stack);
    }

    @Override
    public int fill(FluidStack resource, FluidAction action) {
        Objects.requireNonNull(action, "action");
        if (!canTransact() || !isPlainTaggedWater(resource)) {
            return 0;
        }

        long currentUnits = currentUnits();
        WaterUnitConversions.TransferPlan plan = WaterUnitConversions.planFill(
                currentUnits,
                resource.getAmount()
        );
        if (plan.milliBuckets() <= 0 || plan.deltaUnits() <= 0L) {
            return 0;
        }

        return transferExactly(plan.deltaUnits(), true, action)
                ? plan.milliBuckets()
                : 0;
    }

    @Override
    public FluidStack drain(FluidStack resource, FluidAction action) {
        Objects.requireNonNull(action, "action");
        if (!isPlainTaggedWater(resource)) {
            return FluidStack.EMPTY;
        }
        return drain(resource.getAmount(), action);
    }

    @Override
    public FluidStack drain(int maxDrain, FluidAction action) {
        Objects.requireNonNull(action, "action");
        if (!canTransact() || maxDrain <= 0) {
            return FluidStack.EMPTY;
        }

        long currentUnits = currentUnits();
        WaterUnitConversions.TransferPlan plan = WaterUnitConversions.planDrain(currentUnits, maxDrain);
        if (plan.milliBuckets() <= 0 || plan.deltaUnits() <= 0L) {
            return FluidStack.EMPTY;
        }

        boolean drained = transferExactly(plan.deltaUnits(), false, action);
        return !drained
                ? FluidStack.EMPTY
                : new FluidStack(
                        WildernessFluidRegistry.WILDERNESS_WATER.get(),
                        plan.milliBuckets()
                );
    }

    private boolean canTransact() {
        return compatibilityEnabled.getAsBoolean()
                && WildernessWaterRules.isEnabled(level)
                && level.getServer().isSameThread();
    }

    private long currentUnits() {
        return waterAccess.getWaterUnits(level, position);
    }

    private static boolean isPlainTaggedWater(FluidStack stack) {
        return stack != null
                && !stack.isEmpty()
                && stack.isComponentsPatchEmpty()
                && stack.is(FluidTags.WATER);
    }

    /**
     * Executes one all-or-nothing authority transfer.
     *
     * <p>NeoForge handlers cannot report a hidden fixed-point partial transfer.
     * If execution unexpectedly differs from simulation, the transferred units
     * are immediately rolled back so the caller observes no transaction.</p>
     */
    private boolean transferExactly(long deltaUnits, boolean adding, FluidAction action) {
        WaterInteractionResult result = adding
                ? waterAccess.addWater(level, position, deltaUnits, action.simulate())
                : waterAccess.removeWater(level, position, deltaUnits, action.simulate());
        if (result.transferredUnits() == deltaUnits) {
            return true;
        }
        if (action.simulate() || result.transferredUnits() <= 0L) {
            return false;
        }

        long rollbackUnits = result.transferredUnits();
        WaterInteractionResult rollback = adding
                ? waterAccess.removeWater(level, position, rollbackUnits, false)
                : waterAccess.addWater(level, position, rollbackUnits, false);
        if (rollback.transferredUnits() != rollbackUnits) {
            ModConstants.LOGGER.error(
                    "Failed to roll back partial Wilderness water {} at {}: restored {} of {} units",
                    adding ? "fill" : "drain",
                    position,
                    rollback.transferredUnits(),
                    rollbackUnits
            );
        }
        return false;
    }
}
