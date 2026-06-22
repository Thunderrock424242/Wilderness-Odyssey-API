package com.thunder.wildernessodysseyapi.watersystem.water.sph;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SPHSimulatorMirrorTest {

    @Test
    void remoteMirrorPreservesIdentityAndInterpolatesSnapshots() {
        UUID id = UUID.randomUUID();
        SPHSimulator mirror = SPHSimulator.createRemoteMirror(id, null);
        mirror.applyRemoteSnapshot(List.of(new SPHParticle(0.0f, 2.0f, 0.0f)));
        mirror.applyRemoteSnapshot(List.of(new SPHParticle(4.0f, 2.0f, 0.0f)));

        mirror.tick(0.05f);

        assertEquals(id, mirror.getSimulationId());
        assertEquals(1.0f, mirror.getRenderParticles().getFirst().position.x, 1.0e-6f);
    }

    @Test
    void remoteMirrorExpiresWithoutAuthoritativeSnapshots() {
        SPHSimulator mirror = SPHSimulator.createRemoteMirror(UUID.randomUUID(), null);
        mirror.applyRemoteSnapshot(List.of(new SPHParticle(0.0f, 0.0f, 0.0f)));

        for (int tick = 0; tick <= SPHConstants.REMOTE_SNAPSHOT_EXPIRY_TICKS; tick++) {
            mirror.tick(0.05f);
        }

        assertTrue(mirror.isRemoteExpired());
    }

    @Test
    void persistentRestoreKeepsServerOwnedState() {
        UUID id = UUID.randomUUID();
        SPHParticle particle = new SPHParticle(2.0f, 3.0f, 4.0f);
        particle.velocity.set(0.25f, -0.5f, 0.75f);

        SPHSimulator restored = SPHSimulator.restoreAuthoritative(id, null, List.of(particle));

        assertEquals(id, restored.getSimulationId());
        assertEquals(0.75f, restored.getRenderParticles().getFirst().velocity.z, 1.0e-6f);
        assertTrue(!restored.isRemoteMirror());
    }
}
