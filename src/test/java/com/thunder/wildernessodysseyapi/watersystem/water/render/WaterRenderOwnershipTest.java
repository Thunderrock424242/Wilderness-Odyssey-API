package com.thunder.wildernessodysseyapi.watersystem.water.render;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class WaterRenderOwnershipTest {
    @Test
    void disablingRestoresBakedTopsAndReenablingRebuildsSnapshots() {
        var ownership = new WaterRenderOwnership();
        ownership.update(WaterRenderOwnership.Owner.NATIVE);
        var disabled = ownership.update(WaterRenderOwnership.Owner.FALLBACK);
        assertTrue(disabled.rebuildBaked());
        assertTrue(disabled.releaseResources());
        assertFalse(disabled.rebuildMeshes());
        assertEquals(WaterRenderOwnership.Transition.NONE, ownership.update(WaterRenderOwnership.Owner.FALLBACK));
        var enabled = ownership.update(WaterRenderOwnership.Owner.NATIVE);
        assertTrue(enabled.rebuildBaked());
        assertTrue(enabled.rebuildMeshes());
    }

    @Test
    void shaderOwnerSwitchesInvalidateBothDirectionsAndTeardownResetsState() {
        var ownership = new WaterRenderOwnership();
        ownership.update(WaterRenderOwnership.Owner.NATIVE);
        assertTrue(ownership.update(WaterRenderOwnership.Owner.EXTERNAL).rebuildBaked());
        assertTrue(ownership.update(WaterRenderOwnership.Owner.NATIVE).rebuildMeshes());
        ownership.reset();
        assertEquals(WaterRenderOwnership.Transition.NONE, ownership.update(WaterRenderOwnership.Owner.FALLBACK));
    }
}
