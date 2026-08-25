package com.thunder.wildernessodysseyapi.simulation.debug;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import com.thunder.wildernessodysseyapi.environment.api.EnvironmentServices;
import com.thunder.wildernessodysseyapi.ecosystem.debug.map.EcosystemDebugMapService;
import com.thunder.wildernessodysseyapi.simulation.api.SimulationServices;
import com.thunder.wildernessodysseyapi.simulation.integration.EcosystemSimulationBridge;
import com.thunder.wildernessodysseyapi.simulation.integration.PopulationEcologySimulationSystem;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;

import java.util.Comparator;
import java.util.Locale;

/** Permission-gated diagnostics under the existing {@code /wo} command hierarchy. */
public final class SimulationDebugCommand {
    private SimulationDebugCommand() {
    }

    /** Registers {@code /wo simulation status|region|population|map}. */
    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("wo")
                .requires(source -> source.hasPermission(2))
                .then(Commands.literal("simulation")
                        .then(Commands.literal("status")
                                .executes(SimulationDebugCommand::status))
                        .then(Commands.literal("region")
                                .executes(SimulationDebugCommand::region))
                        .then(Commands.literal("population")
                                .executes(SimulationDebugCommand::population))
                        .then(Commands.literal("map")
                                .executes(SimulationDebugCommand::map))));
    }

    private static int status(CommandContext<CommandSourceStack> context) {
        SimulationDebugSnapshot snapshot = SimulationServices.debugSnapshot();
        CommandSourceStack source = context.getSource();
        source.sendSuccess(() -> Component.literal(
                "WO Simulation Engine running=" + snapshot.running()
                        + " systems=" + snapshot.enabledSystems() + "/" + snapshot.registeredSystems()
                        + " listeners=" + snapshot.eventListeners()
                        + " pressure=" + snapshot.pressure().name().toLowerCase(Locale.ROOT)
        ), false);
        source.sendSuccess(() -> Component.literal(
                "  regions pending/tracked=" + snapshot.pendingRegions() + "/" + snapshot.trackedRegions()
                        + " active/near/distant/dormant=" + snapshot.activeRegions()
                        + "/" + snapshot.nearbyRegions()
                        + "/" + snapshot.backgroundRegions()
                        + "/" + snapshot.dormantRegions()
        ), false);
        source.sendSuccess(() -> Component.literal(
                "  requests accepted/coalesced/rejected=" + snapshot.acceptedRequests()
                        + "/" + snapshot.coalescedRequests()
                        + "/" + snapshot.rejectedRequests()
                        + " processed=" + snapshot.processedRegions()
                        + " deferredPasses=" + snapshot.deferredPasses()
        ), false);
        source.sendSuccess(() -> Component.literal(
                "  events=" + snapshot.eventsDispatched()
                        + " eventFailures=" + snapshot.eventListenerFailures()
                        + " systemFailures=" + snapshot.systemFailures()
                        + " lastPass=" + snapshot.lastPassTick()
                        + " (" + snapshot.lastPassNanos() / 1_000L + " us)"
        ), false);
        snapshot.systems().entrySet().stream()
                .sorted(Comparator.comparing(entry -> entry.getKey().toString()))
                .forEach(entry -> source.sendSuccess(() -> Component.literal(
                        "  " + entry.getKey()
                                + " updates/skips/failures=" + entry.getValue().updates()
                                + "/" + entry.getValue().skipped()
                                + "/" + entry.getValue().failures()
                                + " total/last=" + entry.getValue().totalNanos() / 1_000L
                                + "/" + entry.getValue().lastNanos() / 1_000L + " us"
                ), false));
        return 1;
    }

    private static int region(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        BlockPos position = BlockPos.containing(source.getPosition());
        var environment = EnvironmentServices.query().sample(source.getLevel(), position);
        var activity = EcosystemSimulationBridge.activityAt(source.getLevel(), position);
        var ecosystem = EcosystemSimulationBridge.snapshotAt(source.getLevel(), position);
        source.sendSuccess(() -> Component.literal(
                "WO simulation region=" + EcosystemSimulationBridge.regionAt(source.getLevel(), position)
                        + " activity=" + activity.name().toLowerCase(Locale.ROOT)
                        + " ecosystemGroups=" + ecosystem.map(snapshot -> snapshot.groupCount()).orElse(0)
                        + " population=" + ecosystem.map(snapshot -> snapshot.totalPopulation()).orElse(0)
        ), false);
        source.sendSuccess(() -> Component.literal(String.format(
                Locale.ROOT,
                "  habitat=%.3f water=%.3f wildlife=%.3f migration=%.3f vegetationStress=%.3f hazard=%.3f",
                environment.influence().habitatProductivity(),
                environment.influence().waterAvailability(),
                environment.influence().wildlifeActivity(),
                environment.influence().migrationPressure(),
                environment.influence().vegetationStress(),
                environment.influence().overallHazard()
        )), false);
        return 1;
    }

    private static int population(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        PopulationEcologySimulationSystem.Diagnostics diagnostics = PopulationEcologySimulationSystem.get()
                .diagnostics(source.getLevel());
        source.sendSuccess(() -> Component.literal(
                "WO population ecology enabled=" + diagnostics.enabled()
                        + " interval=" + diagnostics.updateIntervalTicks()
                        + " carryingCapacity=" + diagnostics.regionalCarryingCapacity()
                        + " inFlight=" + diagnostics.inFlight()
                        + " lastApply=" + diagnostics.lastApplyGameTime()
        ), false);
        source.sendSuccess(() -> Component.literal(
                "  regions requested/rejected=" + diagnostics.regionRequests()
                        + "/" + diagnostics.regionRequestRejections()
                        + " batches submitted/rejected/applied/discarded/timeout="
                        + diagnostics.submittedBatches() + "/" + diagnostics.rejectedBatches()
                        + "/" + diagnostics.appliedBatches() + "/" + diagnostics.discardedBatches()
                        + "/" + diagnostics.timedOutSubmissions()
        ), false);
        source.sendSuccess(() -> Component.literal(
                "  groups applied/stale=" + diagnostics.appliedGroups() + "/" + diagnostics.staleGroups()
                        + " animals added/removed=" + diagnostics.animalsAdded()
                        + "/" + diagnostics.animalsRemoved()
        ), false);
        return 1;
    }

    private static int map(CommandContext<CommandSourceStack> context)
            throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        return EcosystemDebugMapService.open(context.getSource().getPlayerOrException()) ? 1 : 0;
    }
}
