package com.thunder.wildernessodysseyapi.watersystem.water.worldgen.river;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class RiverReachFlowTest {
    @Test
    void capturedDirectionUsesModeledMagnitudeAndHydraulicLimit() {
        var reach = new RiverGenerationChunk.Reach(140, 138, 140, 0.6f, 0.8f, 1.2f, RiverChannelColumn.Kind.STREAM);
        var flow = RiverReachServices.current(reach, 0.03f, 0);
        assertEquals(0.018f, flow.currentX(), 0.00001f);
        assertEquals(0.024f, flow.currentZ(), 0.00001f);
        assertEquals(0.06f, RiverReachServices.current(reach, 1, 0).speed(), 0.00001f);
    }

    @Test
    void generatedMetadataDoesNotInventDischargeOrAddLakeCurrents() {
        var reach = new RiverGenerationChunk.Reach(140, 138, 140, 1, 0, 8, RiverChannelColumn.Kind.STREAM);
        assertEquals(0, RiverReachServices.current(reach, 0, 0).speed());
        var lake = new RiverGenerationChunk.Reach(140, 138, 140, 0, 0, 0, RiverChannelColumn.Kind.LAKE);
        assertEquals(0, RiverReachServices.current(lake, 0.1f, 0.1f).speed());
    }
}
