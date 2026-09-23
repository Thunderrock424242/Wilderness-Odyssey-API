package com.thunder.wildernessodysseyapi.temporalrift.echo;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class EchoTimeModelTest {
    @Test
    void disabledAndStableRegionsKeepTheOrdinaryClock() {
        for (long time = -24000; time < 48000; time += 13) {
            assertEquals(time, EchoTimeModel.visualDayTime(time, 5, false, 1));
            assertEquals(time, EchoTimeModel.visualDayTime(time, 5, true, 0.48));
        }
    }

    @Test
    void celestialPauseRecoversWithoutReversingOrAccumulatingClockDrift() {
        boolean paused = false;
        for (long region = 0; region < 8; region++) {
            long previous = -1;
            for (long time = 0; time <= 24000; time++) {
                long visual = EchoTimeModel.visualDayTime(time, region, true, 0.9);
                assertTrue(visual >= previous && visual <= time);
                assertTrue(time - visual <= 40);
                paused |= visual == previous;
                previous = visual;
            }
            assertEquals(24000, previous);
        }
        assertTrue(paused);
    }
}
