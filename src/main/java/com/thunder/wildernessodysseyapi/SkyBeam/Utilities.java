package com.thunder.wildernessodysseyapi.SkyBeam;

import net.minecraft.core.BlockPos;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * A simple tick‐based scheduler: runLater(delayTicks, task) will execute `task.run()`
 * after that many server ticks.
 */
@EventBusSubscriber
public class Utilities {
    private static final List<ScheduledTask> TASKS = new CopyOnWriteArrayList<>();

    /**
     * Schedule a Runnable to run after `delayTicks` server‐ticks.
     */
    public static void runLater(int delayTicks, Runnable task) {
        if (delayTicks < 0) {
            task.run();
        } else {
            TASKS.add(new ScheduledTask(delayTicks, task));
        }
    }

    /**
     * NeoForge ServerTickEvent.Post fires at the end of every server tick.
     */
    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post evt) {
        // Decrement remaining ticks and run/remove tasks in one pass:
        TASKS.removeIf(st -> {
            st.remaining--;
            if (st.remaining <= 0) {
                try {
                    st.task.run();
                } catch (Exception e) {
                    e.printStackTrace();
                }
                return true;  // remove this task
            }
            return false;     // keep it
        });
    }

    private static final class ScheduledTask {
        int remaining;
        final Runnable task;
        ScheduledTask(int remaining, Runnable task) {
            this.remaining = remaining;
            this.task      = task;
        }
    }

    /** iterate positions forming the perimeter of a square of radius r */
    public static List<BlockPos> squareRing(BlockPos c, int r) {
        List<BlockPos> ring = new ArrayList<>();
        for (int dx=-r; dx<=r; dx++) {
            ring.add(c.offset(dx,0, r));
            ring.add(c.offset(dx,0,-r));
        }
        for (int dz=-r+1; dz<=r-1; dz++) {
            ring.add(c.offset(r,0, dz));
            ring.add(c.offset(-r,0, dz));
        }
        return ring;
    }
}
