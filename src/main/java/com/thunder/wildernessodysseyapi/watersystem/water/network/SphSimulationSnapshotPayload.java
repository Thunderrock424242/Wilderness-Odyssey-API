package com.thunder.wildernessodysseyapi.watersystem.water.network;

import com.thunder.wildernessodysseyapi.core.ModConstants;
import com.thunder.wildernessodysseyapi.watersystem.water.sph.SPHConstants;
import com.thunder.wildernessodysseyapi.watersystem.water.sph.SPHParticle;
import com.thunder.wildernessodysseyapi.watersystem.water.sph.SPHSimulator;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Sends one bounded, authoritative SPH body snapshot to a nearby client.
 *
 * <p>The client interpolates these states for rendering and never re-runs the
 * collision simulation. This keeps multiplayer water deterministic and makes
 * the logical server the sole owner of volumetric-water state.</p>
 *
 * @param simulationId stable fluid-body identity
 * @param particles render state for the current server revision
 */
public record SphSimulationSnapshotPayload(
        UUID simulationId,
        List<ParticleSnapshot> particles
) implements CustomPacketPayload {

    private static final float POSITION_QUANTIZATION = 256.0f;
    private static final float VELOCITY_QUANTIZATION = 256.0f;

    public static final Type<SphSimulationSnapshotPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(ModConstants.MOD_ID, "sph_simulation_snapshot")
    );

    public static final StreamCodec<FriendlyByteBuf, SphSimulationSnapshotPayload> STREAM_CODEC =
            StreamCodec.of(SphSimulationSnapshotPayload::encode, SphSimulationSnapshotPayload::decode);

    public SphSimulationSnapshotPayload {
        particles = List.copyOf(particles);
        if (particles.size() > SPHConstants.MAX_PARTICLES) {
            throw new IllegalArgumentException("SPH snapshot exceeds the per-body particle limit");
        }
    }

    /**
     * Copies the simulator's render-safe particle state into a network payload.
     *
     * @param simulator authoritative server simulator
     * @return immutable snapshot payload
     */
    public static SphSimulationSnapshotPayload fromSimulator(SPHSimulator simulator) {
        List<ParticleSnapshot> snapshots = new ArrayList<>(simulator.particleCount());
        for (SPHParticle particle : simulator.getRenderParticles()) {
            snapshots.add(ParticleSnapshot.fromParticle(particle));
        }
        return new SphSimulationSnapshotPayload(simulator.getSimulationId(), snapshots);
    }

    /**
     * Reconstructs render state objects for the client mirror.
     *
     * @return mutable particle copies owned by the receiving mirror
     */
    public List<SPHParticle> toParticles() {
        List<SPHParticle> result = new ArrayList<>(particles.size());
        for (ParticleSnapshot snapshot : particles) {
            result.add(snapshot.toParticle());
        }
        return result;
    }

    private static void encode(FriendlyByteBuf buffer, SphSimulationSnapshotPayload payload) {
        buffer.writeUUID(payload.simulationId);
        buffer.writeVarInt(payload.particles.size());
        float centerX = 0.0f;
        float centerY = 0.0f;
        float centerZ = 0.0f;
        for (ParticleSnapshot particle : payload.particles) {
            centerX += particle.x;
            centerY += particle.y;
            centerZ += particle.z;
        }
        if (!payload.particles.isEmpty()) {
            float inverseCount = 1.0f / payload.particles.size();
            centerX *= inverseCount;
            centerY *= inverseCount;
            centerZ *= inverseCount;
        }
        buffer.writeFloat(centerX);
        buffer.writeFloat(centerY);
        buffer.writeFloat(centerZ);
        for (ParticleSnapshot particle : payload.particles) {
            particle.encode(buffer, centerX, centerY, centerZ);
        }
    }

    private static SphSimulationSnapshotPayload decode(FriendlyByteBuf buffer) {
        UUID simulationId = buffer.readUUID();
        int particleCount = buffer.readVarInt();
        if (particleCount < 0 || particleCount > SPHConstants.MAX_PARTICLES) {
            throw new IllegalArgumentException("Invalid SPH snapshot particle count: " + particleCount);
        }
        float centerX = buffer.readFloat();
        float centerY = buffer.readFloat();
        float centerZ = buffer.readFloat();

        List<ParticleSnapshot> particles = new ArrayList<>(particleCount);
        for (int i = 0; i < particleCount; i++) {
            particles.add(ParticleSnapshot.decode(buffer, centerX, centerY, centerZ));
        }
        return new SphSimulationSnapshotPayload(simulationId, particles);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    /**
     * Compact serializable state needed to interpolate and render one particle.
     */
    public record ParticleSnapshot(
            float x,
            float y,
            float z,
            float velocityX,
            float velocityY,
            float velocityZ,
            boolean droplet,
            int dropletLife
    ) {

        public ParticleSnapshot {
            x = finiteOrZero(x);
            y = finiteOrZero(y);
            z = finiteOrZero(z);
            velocityX = finiteOrZero(velocityX);
            velocityY = finiteOrZero(velocityY);
            velocityZ = finiteOrZero(velocityZ);
            dropletLife = Math.max(0, dropletLife);
        }

        private static ParticleSnapshot fromParticle(SPHParticle particle) {
            return new ParticleSnapshot(
                    particle.position.x,
                    particle.position.y,
                    particle.position.z,
                    particle.velocity.x,
                    particle.velocity.y,
                    particle.velocity.z,
                    particle.isDroplet,
                    particle.dropletLife
            );
        }

        private SPHParticle toParticle() {
            SPHParticle particle = new SPHParticle(x, y, z);
            particle.velocity.set(velocityX, velocityY, velocityZ);
            particle.isDroplet = droplet;
            particle.dropletLife = dropletLife;
            return particle;
        }

        private void encode(FriendlyByteBuf buffer, float centerX, float centerY, float centerZ) {
            buffer.writeShort(quantize(x - centerX));
            buffer.writeShort(quantize(y - centerY));
            buffer.writeShort(quantize(z - centerZ));
            buffer.writeShort(quantizeVelocity(velocityX));
            buffer.writeShort(quantizeVelocity(velocityY));
            buffer.writeShort(quantizeVelocity(velocityZ));
            buffer.writeBoolean(droplet);
            buffer.writeVarInt(dropletLife);
        }

        private static ParticleSnapshot decode(
                FriendlyByteBuf buffer,
                float centerX,
                float centerY,
                float centerZ
        ) {
            return new ParticleSnapshot(
                    centerX + buffer.readShort() / POSITION_QUANTIZATION,
                    centerY + buffer.readShort() / POSITION_QUANTIZATION,
                    centerZ + buffer.readShort() / POSITION_QUANTIZATION,
                    buffer.readShort() / VELOCITY_QUANTIZATION,
                    buffer.readShort() / VELOCITY_QUANTIZATION,
                    buffer.readShort() / VELOCITY_QUANTIZATION,
                    buffer.readBoolean(),
                    buffer.readVarInt()
            );
        }

        private static int quantize(float relativePosition) {
            return Math.max(Short.MIN_VALUE, Math.min(Short.MAX_VALUE,
                    Math.round(relativePosition * POSITION_QUANTIZATION)));
        }

        private static int quantizeVelocity(float velocity) {
            return Math.max(Short.MIN_VALUE, Math.min(Short.MAX_VALUE,
                    Math.round(velocity * VELOCITY_QUANTIZATION)));
        }

        private static float finiteOrZero(float value) {
            return Float.isFinite(value) ? value : 0.0f;
        }
    }
}
