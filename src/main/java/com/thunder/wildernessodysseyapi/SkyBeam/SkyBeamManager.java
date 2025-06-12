package com.thunder.wildernessodysseyapi.SkyBeam;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

public class SkyBeamManager {
    public static final List<SkyBeamData> ACTIVE_BEAMS = new CopyOnWriteArrayList<>();

    public static void addBeam(SkyBeamData beam) {
        ACTIVE_BEAMS.add(beam);
    }

    public static void tick(long gameTime) {
        ACTIVE_BEAMS.removeIf(b -> gameTime - b.startTime() > b.durationTicks());
    }
}
