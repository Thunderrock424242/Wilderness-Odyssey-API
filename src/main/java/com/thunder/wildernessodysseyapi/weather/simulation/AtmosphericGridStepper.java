package com.thunder.wildernessodysseyapi.weather.simulation;

import com.thunder.wildernessodysseyapi.weather.api.*;
import com.thunder.wildernessodysseyapi.weather.system.WeatherSystemInfluenceModel;
import com.thunder.wildernessodysseyapi.weather.system.WeatherSystemTracker;
import com.thunder.wildernessodysseyapi.watersystem.water.hydrology.AtmosphericWaterExchange;
import java.util.*;

/** Synchronous finite-volume generations with bounded work and retained elapsed-time backlog. */
public final class AtmosphericGridStepper {
    private AtmosphericGridStepper() { }

    /** Inputs include no player objects; activity changes admission/detail sampling only. */
    public record Input(AtmosphereView view, AtmosphereEnvironment environment,
                        AtmosphericWaterExchange.Receipt receipt, WeatherSystemTracker.SystemInfluence influence) { }
    public record Output(AtmosphericPhysicalState state, WeatherSample sample, AtmosphericWaterFlux flux, long throughTick) { }
    public record Result(Map<Long, Output> cells, int cellSteps, long deferredTicks, long calculationNanos) { }

    /** All faces read one old generation. A new cell waits at its creation clock while older cells catch up. */
    public static Result advance(List<Input> source, SimulationSettings settings, int cellSize, long targetTick,
                                 int maximumCellSteps) {
        long started = System.nanoTime();
        TreeMap<Long, Input> inputs = new TreeMap<>();
        Map<Long, Output> states = new HashMap<>();
        for (Input input : source) {
            long key = input.view().key().packed();
            inputs.put(key, input);
            states.put(key, new Output(input.view().physicalState(), input.view().sample(),
                    AtmosphericWaterFlux.NONE, input.view().lastSimulatedTick()));
        }
        int work = 0;
        int budget = Math.max(inputs.size(), maximumCellSteps);
        AtmosphereSimulationEngine engine = new AtmosphereSimulationEngine();
        while (!states.isEmpty() && work < budget && settings.simulationSpeed() > 0) {
            long minimumTick = states.values().stream().mapToLong(Output::throughTick).min().orElse(targetTick);
            if (minimumTick >= targetTick) break;
            long nextClock = targetTick;
            List<Long> group = new ArrayList<>();
            for (Map.Entry<Long, Input> entry : inputs.entrySet()) {
                long clock = states.get(entry.getKey()).throughTick();
                if (clock == minimumTick) group.add(entry.getKey());
                else nextClock = Math.min(nextClock, clock);
            }
            double maxStep = AtmosphereSimulationEngine.maximumStepSeconds(cellSize, settings);
            long ticks = Math.min(nextClock - minimumTick, Math.max(1, (long) Math.floor(maxStep * 20)));
            int substeps = Math.max(1, (int) Math.ceil(ticks * .05 / maxStep));
            if (work + group.size() * substeps > budget) break;
            double dt = ticks * .05 / substeps;
            for (int step = 0; step < substeps; step++) {
                Map<Long, Output> next = new HashMap<>(group.size());
                for (long key : group) {
                    Input input = inputs.get(key);
                    Output old = states.get(key);
                    AtmosphereCellKey cell = input.view().key();
                    var neighbors = AtmosphereSimulationEngine.PhysicalNeighborhood.bounded(old.state(),
                            neighbor(states, cell.x(), cell.z() - 1, minimumTick),
                            neighbor(states, cell.x() + 1, cell.z(), minimumTick),
                            neighbor(states, cell.x(), cell.z() + 1, minimumTick),
                            neighbor(states, cell.x() - 1, cell.z(), minimumTick));
                    var receipt = old.flux().dtSeconds() == 0 ? input.receipt() : input.receipt().withoutFlux();
                    var calculated = engine.simulatePhysical(old.state(), input.environment(), neighbors, settings,
                            dt, cellSize, receipt);
                    WeatherSample feedback = WeatherSystemInfluenceModel.apply(calculated.sample(), input.influence(),
                            AtmosphericUnits.legacyResponse(.1, dt * settings.simulationSpeed()));
                    AtmosphericPhysicalState physical = calculated.state().withDynamics(feedback);
                    next.put(key, new Output(physical, physical.toWeatherSample(physical.surface(), calculated.sample().precipitationType()),
                            old.flux().plus(calculated.flux()), minimumTick));
                }
                states.putAll(next);
                work += group.size();
            }
            for (long key : group) {
                Output result = states.get(key);
                states.put(key, new Output(result.state(), result.sample(), result.flux(), minimumTick + ticks));
            }
        }
        long deferred = 0;
        for (Output output : states.values()) deferred = Math.max(deferred, Math.max(0, targetTick - output.throughTick()));
        return new Result(Map.copyOf(states), work, deferred, System.nanoTime() - started);
    }

    private static AtmosphericPhysicalState neighbor(Map<Long, Output> states, int x, int z, long clock) {
        Output value = states.get(new AtmosphereCellKey(x, z).packed());
        return value != null && value.throughTick() == clock ? value.state() : null;
    }
}
