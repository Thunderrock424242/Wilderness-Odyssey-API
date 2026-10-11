package com.thunder.wildernessodysseyapi.anomaly;

import org.junit.jupiter.api.Test;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class AnomalyPreviewOverridesTest {
    @Test
    void previewExpiresAndDoesNotCrossDimensions() {
        var overrides = new AnomalyPreviewOverrides();
        UUID player = UUID.randomUUID();
        overrides.set(player, "minecraft:overworld", 4, 0.7, 100, 200);
        assertEquals(0.7, overrides.get(player, "minecraft:overworld", 150).orElseThrow().intensity());
        assertTrue(overrides.get(player, "minecraft:overworld", 200).isEmpty());
        overrides.set(player, "minecraft:overworld", 1, 0.3, 100, 200);
        assertTrue(overrides.get(player, "wildernessodysseyapi:anomaly_dimension", 150).isEmpty());
        assertTrue(overrides.get(player, "minecraft:overworld", 150).isEmpty());
    }

    @Test
    void rejectsNonFiniteStrengthAndUnknownZones() {
        var overrides = new AnomalyPreviewOverrides();
        UUID player = UUID.randomUUID();
        assertThrows(IllegalArgumentException.class,
                () -> overrides.set(player, "minecraft:overworld", 5, 0.7, 100, 200));
        assertThrows(IllegalArgumentException.class,
                () -> overrides.set(player, "minecraft:overworld", 0, Double.NaN, 100, 200));
    }

    @Test
    void lifecycleClearAndClockResetDiscardPreview() {
        var overrides = new AnomalyPreviewOverrides();
        UUID player = UUID.randomUUID();
        overrides.set(player, "minecraft:overworld", 0, 0.2, 100, 200);
        assertTrue(overrides.get(player, "minecraft:overworld", 50).isEmpty());
        overrides.set(player, "minecraft:overworld", 0, 0.2, 100, 200);
        overrides.clear(player);
        assertTrue(overrides.get(player, "minecraft:overworld", 150).isEmpty());
    }
}
