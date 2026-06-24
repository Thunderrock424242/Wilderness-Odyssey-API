package com.thunder.wildernessodysseyapi.watersystem.water.network;

import com.thunder.wildernessodysseyapi.watersystem.water.sph.SPHParticle;
import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SphSimulationSnapshotPayloadTest {

    @Test
    void reconstructedParticlesKeepAuthoritativeVelocity() {
        var snapshot = new SphSimulationSnapshotPayload.ParticleSnapshot(
                1.0f,
                2.0f,
                3.0f,
                0.25f,
                -0.5f,
                0.75f,
                true,
                12
        );
        var payload = new SphSimulationSnapshotPayload(UUID.randomUUID(), List.of(snapshot));

        SPHParticle particle = payload.toParticles().getFirst();

        assertEquals(0.25f, particle.velocity.x, 1.0e-6f);
        assertEquals(-0.5f, particle.velocity.y, 1.0e-6f);
        assertEquals(0.75f, particle.velocity.z, 1.0e-6f);
        assertEquals(12, particle.dropletLife);
    }

    @Test
    void codecRoundTripKeepsQuantizedVelocity() {
        var snapshot = new SphSimulationSnapshotPayload.ParticleSnapshot(
                24.5f,
                63.25f,
                -18.75f,
                1.25f,
                -2.5f,
                3.75f,
                false,
                0
        );
        var payload = new SphSimulationSnapshotPayload(UUID.randomUUID(), List.of(snapshot));
        FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
        try {
            SphSimulationSnapshotPayload.STREAM_CODEC.encode(buffer, payload);
            SPHParticle decoded = SphSimulationSnapshotPayload.STREAM_CODEC.decode(buffer)
                    .toParticles()
                    .getFirst();

            assertEquals(1.25f, decoded.velocity.x, 1.0f / 256.0f);
            assertEquals(-2.5f, decoded.velocity.y, 1.0f / 256.0f);
            assertEquals(3.75f, decoded.velocity.z, 1.0f / 256.0f);
        } finally {
            buffer.release();
        }
    }

    @Test
    void nonFiniteNetworkStateIsSanitized() {
        var snapshot = new SphSimulationSnapshotPayload.ParticleSnapshot(
                Float.NaN,
                Float.POSITIVE_INFINITY,
                Float.NEGATIVE_INFINITY,
                Float.NaN,
                Float.POSITIVE_INFINITY,
                Float.NEGATIVE_INFINITY,
                false,
                -4
        );

        assertEquals(0.0f, snapshot.x());
        assertEquals(0.0f, snapshot.y());
        assertEquals(0.0f, snapshot.z());
        assertEquals(0.0f, snapshot.velocityX());
        assertEquals(0.0f, snapshot.velocityY());
        assertEquals(0.0f, snapshot.velocityZ());
        assertEquals(0, snapshot.dropletLife());
    }
}
