package com.thunder.wildernessodysseyapi.performance.background;

/**
 * Orders Wilderness Odyssey background work from player-critical to opportunistic.
 *
 * <p>The order is intentionally independent of Minecraft's own tick queues. Only
 * tasks explicitly submitted by Wilderness Odyssey systems use these priorities.</p>
 */
public enum WorkPriority {
    CRITICAL,
    GAMEPLAY,
    NORMAL,
    BACKGROUND,
    IDLE
}
