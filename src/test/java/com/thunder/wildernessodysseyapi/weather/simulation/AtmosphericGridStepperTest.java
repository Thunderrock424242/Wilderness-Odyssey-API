package com.thunder.wildernessodysseyapi.weather.simulation;

import com.thunder.wildernessodysseyapi.weather.api.*;
import com.thunder.wildernessodysseyapi.weather.system.WeatherSystemTracker;
import com.thunder.wildernessodysseyapi.watersystem.water.hydrology.AtmosphericWaterExchange;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** Exercises real synchronous grid generations, retained clocks and closed shared faces. */
class AtmosphericGridStepperTest {
    @Test
    void zeroSpeedConsumesPausedTimeWithoutTakingPendingWater() {
        var inputs = grid(256, 0);
        var paused = new SimulationSettings(0, .18, .1, .2, .12, .72, .58, .42, 1, 0, .75);
        var result = AtmosphericGridStepper.advance(inputs, paused, 256, 600, 16384);
        assertEquals(0, result.cellSteps());
        assertEquals(0, result.deferredTicks());
        for (var input : inputs) {
            var output = result.cells().get(input.view().key().packed());
            assertEquals(input.view().physicalState(), output.state());
            assertEquals(600, output.throughTick());
            assertEquals(AtmosphericWaterFlux.NONE, output.flux());
        }
        var resumed = AtmosphericGridStepper.advance(resume(inputs, result), SimulationSettings.DEFAULT, 256, 620, 16384);
        assertTrue(resumed.cells().values().stream().allMatch(o -> o.flux().dtSeconds() == 1));
    }
    @Test
    void closedGridConservesWaterAcrossManySharedFaceGenerations() {
        List<AtmosphericGridStepper.Input> inputs = grid(256, 0);
        double initial = inputs.stream().mapToDouble(i -> i.view().physicalState().totalWaterKgPerSquareMetre()).sum();
        var result = AtmosphericGridStepper.advance(inputs, SimulationSettings.DEFAULT, 256, 1200, 16384);
        double finalWater = result.cells().values().stream().mapToDouble(o -> o.state().totalWaterKgPerSquareMetre()
                + o.flux().precipitationMm()).sum();
        assertEquals(initial, finalWater, 1.0E-9);
        assertEquals(0, result.cells().values().stream().mapToDouble(o -> o.flux().advectedWaterMm()).sum(), 1.0E-9);
        assertEquals(0, result.deferredTicks());
        assertTrue(result.cells().values().stream().anyMatch(o -> o.flux().precipitationMm() > 0));
    }

    @Test
    void playerInterestDoesNotChangeRetainedCoarseEvolution() {
        var absent = AtmosphericGridStepper.advance(grid(256, 0), SimulationSettings.DEFAULT, 256, 600, 16384);
        var present = AtmosphericGridStepper.advance(grid(256, 600), SimulationSettings.DEFAULT, 256, 600, 16384);
        assertEquals(absent.cells(), present.cells());
    }

    @Test
    void smallWorkBudgetsRetainTimeAndApplyEvaporationOnlyOnce() {
        var inputs = grid(256, 0);
        var first = inputs.getFirst();
        var receipt = new AtmosphericWaterExchange.Receipt(0, 4096000, 1, 0, 256 * 256, List.of());
        inputs.set(0, new AtmosphericGridStepper.Input(first.view(), first.environment(), receipt, first.influence()));
        var complete = AtmosphericGridStepper.advance(inputs, SimulationSettings.DEFAULT, 256, 600, 16384);
        var deferred = AtmosphericGridStepper.advance(inputs, SimulationSettings.DEFAULT, 256, 600, 4);
        assertTrue(deferred.deferredTicks() > 0);
        double credited = deferred.cells().values().stream().mapToDouble(o -> o.flux().evaporationMm()).sum();
        int batches = 1;
        while (deferred.deferredTicks() > 0 && batches++ < 100) {
            inputs = resume(inputs, deferred);
            deferred = AtmosphericGridStepper.advance(inputs, SimulationSettings.DEFAULT, 256, 600, 4);
            credited += deferred.cells().values().stream().mapToDouble(o -> o.flux().evaporationMm()).sum();
        }
        assertEquals(0, deferred.deferredTicks());
        assertEquals(1000.0 / (256 * 256), credited, 1.0E-12);
        for (long key : complete.cells().keySet()) {
            assertEquals(complete.cells().get(key).state(), deferred.cells().get(key).state());
        }
    }

    @Test
    void smallestCellsAtMaximumSpeedStillAdvanceACompleteTick() {
        var settings = new SimulationSettings(8, .18, .1, .2, .12, .72, .58, .42, 1, 0, .75);
        var result = AtmosphericGridStepper.advance(grid(16, 0), settings, 16, 20, 4);
        assertTrue(result.cellSteps() >= 4);
        assertTrue(result.deferredTicks() < 20);
        assertTrue(result.cells().values().stream().allMatch(o -> o.throughTick() > 0));
    }

    @Test
    void supportedSchedulingIntervalsProduceSimilarPhysicalStates() {
        var frequent = scheduled(20);
        var sparse = scheduled(60);
        for (int i = 0; i < frequent.size(); i++) {
            var a = frequent.get(i).view().physicalState();
            var b = sparse.get(i).view().physicalState();
            assertEquals(a.temperatureCelsius(), b.temperatureCelsius(), .15);
            assertEquals(a.totalWaterKgPerSquareMetre(), b.totalWaterKgPerSquareMetre(), .03);
            assertEquals(a.column().surface().windXMetresPerSecond(), b.column().surface().windXMetresPerSecond(), .1);
        }
    }

    @Test
    void differentlyTimedNeighborsCatchUpWithoutExportingUnpairedWater() {
        var inputs = grid(256, 0);
        var old = inputs.getFirst();
        var view = old.view();
        inputs.set(0, new AtmosphericGridStepper.Input(new AtmosphereView(view.key(), view.sample(), 1, 100, 0,
                view.physicalState(), view.environment()), old.environment(), old.receipt(), old.influence()));
        double initial = inputs.stream().mapToDouble(i -> i.view().physicalState().totalWaterKgPerSquareMetre()).sum();
        var result = AtmosphericGridStepper.advance(inputs, SimulationSettings.DEFAULT, 256, 200, 16384);
        assertTrue(result.cells().values().stream().allMatch(o -> o.throughTick() == 200));
        assertEquals(initial, result.cells().values().stream().mapToDouble(o -> o.state().totalWaterKgPerSquareMetre()
                + o.flux().precipitationMm()).sum(), 1.0E-9);
    }

    private static List<AtmosphericGridStepper.Input> scheduled(int interval) {
        var inputs = grid(256, 0);
        for (int tick = interval; tick <= 600; tick += interval) {
            inputs = resume(inputs, AtmosphericGridStepper.advance(inputs, SimulationSettings.DEFAULT, 256, tick, 16384));
        }
        return inputs;
    }

    private static ArrayList<AtmosphericGridStepper.Input> grid(int size, long activeTick) {
        var result = new ArrayList<AtmosphericGridStepper.Input>();
        for (int x = 0; x < 2; x++) for (int z = 0; z < 2; z++) {
            var sample = new WeatherSample(12 + x, .8 + .1 * z, 1, new WindVector(.15, -.1),
                    .65 + x * .1, .3, .3, .1, PrecipitationType.RAIN);
            var view = new AtmosphereView(new AtmosphereCellKey(x, z), sample, 1, 0, activeTick);
            var closed = new AtmosphericWaterExchange.Receipt(0, 0, 1, 0, size * size, List.of());
            result.add(new AtmosphericGridStepper.Input(view, view.environment(), closed,
                    WeatherSystemTracker.SystemInfluence.NONE));
        }
        return result;
    }

    private static ArrayList<AtmosphericGridStepper.Input> resume(List<AtmosphericGridStepper.Input> inputs,
            AtmosphericGridStepper.Result result) {
        var next = new ArrayList<AtmosphericGridStepper.Input>();
        for (var input : inputs) {
            var output = result.cells().get(input.view().key().packed());
            var view = new AtmosphereView(input.view().key(), output.sample(), input.view().revision() + 1,
                    output.throughTick(), input.view().lastActiveTick(), output.state(), input.environment());
            next.add(new AtmosphericGridStepper.Input(view, input.environment(),
                    output.flux().dtSeconds() > 0 ? input.receipt().withoutFlux() : input.receipt(), input.influence()));
        }
        return next;
    }
}
