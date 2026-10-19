package com.thunder.wildernessodysseyapi.watersystem.water.worldgen.river.bank;

import com.thunder.wildernessodysseyapi.watersystem.water.worldgen.river.RiverChannelColumn;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Pure bounded shore candidates; live terrain and plant survival are certified by the decorator. */
final class RiverBankCandidates {
    private static final int[][] DIRECTIONS = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
    private RiverBankCandidates() { }

    static List<Column> select(List<RiverChannelColumn> columns, int offsetX, int offsetZ, int maximum, long seed) {
        if ((offsetX != 0 && offsetX != 16) || (offsetZ != 0 && offsetZ != 16)
                || maximum < 0 || maximum > 256 || columns.size() > 1024) throw new IllegalArgumentException("Unbounded bank candidate request");
        if (maximum == 0 || columns.isEmpty()) return List.of();
        boolean[] wet = new boolean[1024];
        boolean[] selected = new boolean[256];
        for (var column : columns) wet[column.x() + column.z() * 32] = true;
        List<Column> result = new ArrayList<>();
        for (var column : columns) for (int[] direction : DIRECTIONS) {
            int tileX = column.x() + direction[0];
            int tileZ = column.z() + direction[1];
            int x = tileX - offsetX;
            int z = tileZ - offsetZ;
            if (x < 0 || x >= 16 || z < 0 || z >= 16 || wet[tileX + tileZ * 32] || selected[x + z * 16]) continue;
            selected[x + z * 16] = true;
            result.add(new Column(x, z));
        }
        result.sort(Comparator.comparingLong((Column column) -> RiverBankProfiles.mix(seed ^ (column.x() + column.z() * 16)))
                .thenComparingInt(column -> column.x() + column.z() * 16));
        return List.copyOf(result.subList(0, Math.min(maximum, result.size())));
    }

    record Column(int x, int z) { }
}
