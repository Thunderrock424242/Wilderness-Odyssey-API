package com.thunder.wildernessodysseyapi.ecosystem.behavior;

import com.thunder.wildernessodysseyapi.ecosystem.api.ActivityTime;

import java.util.UUID;

/** Pure deterministic daily-schedule calculations shared by live and abstract simulation. */
public final class WildlifeSchedule {

    public static final long DAY_TICKS = 24_000L;

    private WildlifeSchedule() {
    }

    /** Returns a stable per-animal offset so a species does not transition in lockstep. */
    public static int deterministicOffset(UUID animalId, int maximumJitterTicks) {
        if (maximumJitterTicks <= 0) {
            return 0;
        }
        long mixed = animalId.getMostSignificantBits()
                ^ Long.rotateLeft(animalId.getLeastSignificantBits(), 23);
        long width = (long) maximumJitterTicks * 2L + 1L;
        return (int) (Math.floorMod(mixed, width) - maximumJitterTicks);
    }

    /** Classifies the shifted Minecraft day into active, resting, or sleeping time. */
    public static Period period(ActivityTime activeTime, long dayTime, int scheduleOffsetTicks) {
        long time = Math.floorMod(dayTime + scheduleOffsetTicks, DAY_TICKS);
        return switch (activeTime) {
            case DIURNAL -> {
                if (time < 12_000L) {
                    yield Period.ACTIVE;
                }
                yield time < 13_500L || time >= 23_000L ? Period.REST : Period.SLEEP;
            }
            case NOCTURNAL -> {
                if (time >= 13_000L && time < 23_000L) {
                    yield Period.ACTIVE;
                }
                yield time >= 11_500L || time < 1_000L ? Period.REST : Period.SLEEP;
            }
            case CREPUSCULAR -> {
                boolean dawn = time < 3_000L || time >= 21_500L;
                boolean dusk = time >= 9_000L && time < 13_500L;
                if (dawn || dusk) {
                    yield Period.ACTIVE;
                }
                yield time < 9_000L ? Period.REST : Period.SLEEP;
            }
            case FLEXIBLE -> Period.ACTIVE;
        };
    }

    /** Returns whether the shifted time is broadly around local midday. */
    public static boolean isMidday(long dayTime, int scheduleOffsetTicks) {
        long time = Math.floorMod(dayTime + scheduleOffsetTicks, DAY_TICKS);
        return time >= 4_500L && time < 7_500L;
    }

    /** Broad schedule state used without allocating a behavior tree. */
    public enum Period {
        ACTIVE,
        REST,
        SLEEP
    }
}
