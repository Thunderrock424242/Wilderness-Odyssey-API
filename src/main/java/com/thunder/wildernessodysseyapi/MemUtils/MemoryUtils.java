package com.thunder.wildernessodysseyapi.MemUtils;

import static com.thunder.wildernessodysseyapi.Core.ModConstants.LOGGER;

/****
 * MemoryUtils for the Wilderness Odyssey API mod.
 */
public class MemoryUtils {

    // Base recommended MB; adjust as needed
    private static final int BASE_RECOMMENDED_MB = 4096; // 4GB

    /**
     * Tracks the peak memory usage observed during this session in MB.
     */
    private static long peakUsedMB = 0;

    /**
     * Samples the current memory usage and updates the {@code peakUsedMB} if a
     * new high watermark is observed. This method is lightweight and intended to
     * be called frequently (e.g., every tick).
     */
    public static void recordPeakUsage() {
        long used = getUsedMemoryMB();
        if (used > peakUsedMB) {
            peakUsedMB = used;
            if (LOGGER.isDebugEnabled()) {
                LOGGER.debug("New peak memory usage recorded: {} MB", peakUsedMB);
            }
        }
    }

    /**
     * Returns the highest memory usage recorded so far in MB.
     */
    public static long getPeakUsedMemoryMB() {
        return peakUsedMB;
    }

    /**
     * Returns the amount of used memory in MB. Logs only when the value changes
     * to avoid spamming the console on frequent calls.
     */
    private static long lastLoggedUsedMB = -1;
    public static long getUsedMemoryMB() {
        long free  = Runtime.getRuntime().freeMemory();
        long total = Runtime.getRuntime().totalMemory();
        return (total - free) / (1024 * 1024);
        long used = (total - free) / (1024 * 1024);
        if (used != lastLoggedUsedMB) {
            lastLoggedUsedMB = used;
            LOGGER.debug("Calculated used memory: {} MB (total={} MB, free={} MB)",
                    used, total / (1024 * 1024), free / (1024 * 1024));
        }
        return used;
    }

    /**
     * Returns the total allocated memory (heap) in MB. Logs only when the value
     * changes to reduce noise.
     */
    private static long lastLoggedTotalMB = -1;
    public static long getTotalMemoryMB() {
        long total = Runtime.getRuntime().totalMemory();
        return total / (1024 * 1024);
        long totalMB = total / (1024 * 1024);
        if (totalMB != lastLoggedTotalMB) {
            lastLoggedTotalMB = totalMB;
            LOGGER.debug("Total memory allocated: {} MB", totalMB);
        }
        return totalMB;
    }

    /**
     * Very basic heuristic for recommended RAM:
     * - Start at 4GB (4096MB).
     * - Add 128MB for every 10 mods.
     * - If current usage is already higher than that naive guess,
     *   bump recommended to current usage + 512MB.
     */
    private static int lastRecommendedMB = -1;
    public static int calculateRecommendedRAM(long currentUsedMB, int modCount) {
        if (LOGGER.isDebugEnabled()) {
            LOGGER.debug("Calculating recommended RAM with currentUsedMB={} and modCount={}", currentUsedMB, modCount);
        }
        int extraPer10Mods = (modCount / 10) * 128;
        int recommendedMB = BASE_RECOMMENDED_MB + extraPer10Mods;

        if (currentUsedMB > recommendedMB) {
            recommendedMB = (int) currentUsedMB + 512;
        }
        if (LOGGER.isDebugEnabled()) {
        if (recommendedMB != lastRecommendedMB) {
            lastRecommendedMB = recommendedMB;
            LOGGER.debug("Recommended RAM determined to be {} MB", recommendedMB);
        }
        return recommendedMB;
    }
}
