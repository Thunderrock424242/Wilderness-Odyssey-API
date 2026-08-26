package com.thunder.wildernessodysseyapi.cinematic;

import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CinematicTimelineTest {
    private static final CinematicStage FIRST = stage("first", 2);
    private static final CinematicStage SECOND = stage("second", 3);

    @Test
    void advancesOnlyAtStageBoundaries() {
        CinematicTimeline timeline = new CinematicTimeline(List.of(FIRST, SECOND));

        assertEquals(CinematicTimeline.AdvanceResult.NONE, timeline.advance());
        assertEquals(FIRST, timeline.stage());
        assertEquals(1, timeline.stageElapsedTicks());

        assertEquals(CinematicTimeline.AdvanceResult.STAGE_CHANGED, timeline.advance());
        assertEquals(SECOND, timeline.stage());
        assertEquals(0, timeline.stageElapsedTicks());
    }

    @Test
    void completesAfterTheFinalStageDuration() {
        CinematicTimeline timeline = new CinematicTimeline(List.of(FIRST, SECOND));

        timeline.advance();
        timeline.advance();
        timeline.advance();
        timeline.advance();
        assertEquals(CinematicTimeline.AdvanceResult.COMPLETE, timeline.advance());
        assertEquals(SECOND, timeline.stage());
        assertEquals(3, timeline.stageElapsedTicks());
    }

    private static CinematicStage stage(String path, int duration) {
        return new CinematicStage(
                ResourceLocation.fromNamespaceAndPath("wildernessodysseyapi", path),
                duration,
                CinematicControlPolicy.LOCKED,
                true
        );
    }
}
