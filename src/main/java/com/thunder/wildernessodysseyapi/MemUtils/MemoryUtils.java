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
     * Returns the amount of used memory in MB.
     */
    public static long getUsedMemoryMB() {
        long free  = Runtime.getRuntime().freeMemory();
        long total = Runtime.getRuntime().totalMemory();
        return (total - free) / (1024 * 1024);
    }

    /**
     * Returns the total allocated memory (heap) in MB.
     */
    public static long getTotalMemoryMB() {
        long total = Runtime.getRuntime().totalMemory();
        return total / (1024 * 1024);
    }

    /**
     * Very basic heuristic for recommended RAM:
     * - Start at 4GB (4096MB).
     * - Add 128MB for every 10 mods.
     * - If current usage is already higher than that naive guess,
     *   bump recommended to current usage + 512MB.
     */
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
            LOGGER.debug("Recommended RAM determined to be {} MB", recommendedMB);
        }
        return recommendedMB;
    }
}
