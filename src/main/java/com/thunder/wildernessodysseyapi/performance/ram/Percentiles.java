package com.thunder.wildernessodysseyapi.performance.ram;

import java.util.ArrayList;
import java.util.List;

final class Percentiles {
    private Percentiles() {}

    static long percentile(List<Long> source, double percentile) {
        if (source.isEmpty()) return 0L;
        List<Long> sorted = new ArrayList<>(source);
        sorted.sort(Long::compareTo);
        double p = Math.max(0.0, Math.min(1.0, percentile));
        int index = (int) Math.ceil(p * sorted.size()) - 1;
        index = Math.max(0, Math.min(sorted.size() - 1, index));
        return sorted.get(index);
    }
}
