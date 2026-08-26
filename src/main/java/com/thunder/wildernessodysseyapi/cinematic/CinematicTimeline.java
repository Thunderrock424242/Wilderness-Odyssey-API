package com.thunder.wildernessodysseyapi.cinematic;

import java.util.List;
import java.util.Objects;

/** Allocation-free mutable cursor over an immutable sequence stage list. */
public final class CinematicTimeline {
    private final List<CinematicStage> stages;
    private int stageIndex;
    private int stageElapsedTicks;

    public CinematicTimeline(List<CinematicStage> stages) {
        this.stages = List.copyOf(Objects.requireNonNull(stages, "stages"));
        if (this.stages.isEmpty()) {
            throw new IllegalArgumentException("A cinematic sequence requires at least one stage");
        }
    }

    public CinematicStage stage() {
        return stages.get(stageIndex);
    }

    public int stageIndex() {
        return stageIndex;
    }

    public int stageElapsedTicks() {
        return stageElapsedTicks;
    }

    /** Advances one server tick and reports only stage boundaries or completion. */
    public AdvanceResult advance() {
        stageElapsedTicks++;
        if (stageElapsedTicks < stage().durationTicks()) {
            return AdvanceResult.NONE;
        }
        if (stageIndex + 1 >= stages.size()) {
            return AdvanceResult.COMPLETE;
        }
        stageIndex++;
        stageElapsedTicks = 0;
        return AdvanceResult.STAGE_CHANGED;
    }

    public enum AdvanceResult {
        NONE,
        STAGE_CHANGED,
        COMPLETE
    }
}
