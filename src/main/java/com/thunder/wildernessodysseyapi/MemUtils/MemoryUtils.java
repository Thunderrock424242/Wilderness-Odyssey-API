package com.thunder.wildernessodysseyapi.MemUtils;

public class MemoryUtils {

    // Base recommended MB; adjust as needed
    private static final int BASE_RECOMMENDED_MB = 4096; // 4GB

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
        int extraPer10Mods = (modCount / 10) * 128;
        int recommendedMB = BASE_RECOMMENDED_MB + extraPer10Mods;

        if (currentUsedMB > recommendedMB) {
            recommendedMB = (int) currentUsedMB + 512;
        }
        return recommendedMB;
    }
}
