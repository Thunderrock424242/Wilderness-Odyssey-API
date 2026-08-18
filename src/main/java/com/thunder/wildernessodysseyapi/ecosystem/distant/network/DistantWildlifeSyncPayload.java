package com.thunder.wildernessodysseyapi.ecosystem.distant.network;

import com.thunder.wildernessodysseyapi.core.ModConstants;
import com.thunder.wildernessodysseyapi.ecosystem.config.EcosystemConfig;
import com.thunder.wildernessodysseyapi.ecosystem.distant.DistantWildlifeForm;
import com.thunder.wildernessodysseyapi.ecosystem.distant.DistantWildlifeGroup;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Infrequent, bounded replacement snapshot of ecosystem-owned distant groups.
 *
 * <p>There is deliberately no packet per represented animal and no client-to-
 * server counterpart. Clients extrapolate group movement from the shared
 * anchor, direction, speed, reference time, and deterministic seed.</p>
 */
public record DistantWildlifeSyncPayload(
        ResourceLocation dimension,
        int dataVersion,
        long sequence,
        boolean enabled,
        long serverGameTime,
        int realEntityDistance,
        int distantWildlifeDistance,
        int transitionBuffer,
        int updateInterval,
        List<GroupSnapshot> groups
) implements CustomPacketPayload {
    public static final int DATA_VERSION = 1;
    public static final int MAXIMUM_GROUPS = 256;
    public static final int MAXIMUM_REPRESENTED_ANIMALS = 4_096;

    /** Payload identifier used by NeoForge's play protocol. */
    public static final Type<DistantWildlifeSyncPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(ModConstants.MOD_ID, "distant_wildlife")
    );
    /** Strict codec with hard count and value bounds before client mutation. */
    public static final StreamCodec<FriendlyByteBuf, DistantWildlifeSyncPayload> STREAM_CODEC =
            StreamCodec.of(DistantWildlifeSyncPayload::encode, DistantWildlifeSyncPayload::decode);

    public DistantWildlifeSyncPayload {
        dimension = Objects.requireNonNull(dimension, "dimension");
        if (dataVersion != DATA_VERSION) {
            throw new IllegalArgumentException("Unsupported distant wildlife data version: " + dataVersion);
        }
        if (sequence < 0L || serverGameTime < 0L) {
            throw new IllegalArgumentException("Distant wildlife sequence and time must be non-negative");
        }
        validateDistances(realEntityDistance, distantWildlifeDistance, transitionBuffer, updateInterval);
        groups = groups == null ? List.of() : List.copyOf(groups);
        if (groups.size() > MAXIMUM_GROUPS) {
            throw new IllegalArgumentException("Distant wildlife payload exceeds " + MAXIMUM_GROUPS + " groups");
        }
        int representedAnimals = groups.stream().mapToInt(GroupSnapshot::populationEstimate).sum();
        if (representedAnimals > MAXIMUM_REPRESENTED_ANIMALS) {
            throw new IllegalArgumentException(
                    "Distant wildlife payload exceeds " + MAXIMUM_REPRESENTED_ANIMALS + " represented animals"
            );
        }
        if (!enabled && !groups.isEmpty()) {
            throw new IllegalArgumentException("Disabled distant wildlife payload must be empty");
        }
    }

    /** Creates one explicit client reset while preserving the configured debug header. */
    public static DistantWildlifeSyncPayload disabled(
            ResourceLocation dimension,
            long sequence,
            long serverGameTime,
            EcosystemConfig.DistantWildlifeSettings settings
    ) {
        return new DistantWildlifeSyncPayload(
                dimension,
                DATA_VERSION,
                sequence,
                false,
                serverGameTime,
                settings.realEntityDistance(),
                settings.distantWildlifeDistance(),
                settings.transitionBuffer(),
                settings.updateInterval(),
                List.of()
        );
    }

    private static void encode(FriendlyByteBuf buffer, DistantWildlifeSyncPayload payload) {
        buffer.writeResourceLocation(payload.dimension);
        buffer.writeVarInt(payload.dataVersion);
        buffer.writeVarLong(payload.sequence);
        buffer.writeBoolean(payload.enabled);
        buffer.writeVarLong(payload.serverGameTime);
        buffer.writeVarInt(payload.realEntityDistance);
        buffer.writeVarInt(payload.distantWildlifeDistance);
        buffer.writeVarInt(payload.transitionBuffer);
        buffer.writeVarInt(payload.updateInterval);
        buffer.writeVarInt(payload.groups.size());
        for (GroupSnapshot group : payload.groups) {
            buffer.writeVarLong(group.id);
            buffer.writeResourceLocation(group.species);
            buffer.writeVarInt(group.populationEstimate);
            buffer.writeDouble(group.anchorX);
            buffer.writeDouble(group.anchorY);
            buffer.writeDouble(group.anchorZ);
            buffer.writeFloat(group.directionX);
            buffer.writeFloat(group.directionZ);
            buffer.writeFloat(group.speed);
            buffer.writeLong(group.seed);
            buffer.writeVarLong(group.referenceGameTime);
            buffer.writeEnum(group.form);
        }
    }

    private static DistantWildlifeSyncPayload decode(FriendlyByteBuf buffer) {
        ResourceLocation dimension = buffer.readResourceLocation();
        int dataVersion = buffer.readVarInt();
        long sequence = buffer.readVarLong();
        boolean enabled = buffer.readBoolean();
        long serverGameTime = buffer.readVarLong();
        int realDistance = buffer.readVarInt();
        int distantDistance = buffer.readVarInt();
        int transitionBuffer = buffer.readVarInt();
        int updateInterval = buffer.readVarInt();
        int groupCount = buffer.readVarInt();
        if (groupCount < 0 || groupCount > MAXIMUM_GROUPS) {
            throw new IllegalArgumentException("Invalid distant wildlife group count: " + groupCount);
        }
        List<GroupSnapshot> groups = new ArrayList<>(groupCount);
        for (int index = 0; index < groupCount; index++) {
            groups.add(new GroupSnapshot(
                    buffer.readVarLong(),
                    buffer.readResourceLocation(),
                    buffer.readVarInt(),
                    buffer.readDouble(),
                    buffer.readDouble(),
                    buffer.readDouble(),
                    buffer.readFloat(),
                    buffer.readFloat(),
                    buffer.readFloat(),
                    buffer.readLong(),
                    buffer.readVarLong(),
                    buffer.readEnum(DistantWildlifeForm.class)
            ));
        }
        return new DistantWildlifeSyncPayload(
                dimension, dataVersion, sequence, enabled, serverGameTime,
                realDistance, distantDistance, transitionBuffer, updateInterval, groups
        );
    }

    private static void validateDistances(
            int realEntityDistance,
            int distantWildlifeDistance,
            int transitionBuffer,
            int updateInterval
    ) {
        if (realEntityDistance < 32 || realEntityDistance > 512) {
            throw new IllegalArgumentException("Invalid real entity distance: " + realEntityDistance);
        }
        if (transitionBuffer < 8 || transitionBuffer > 256) {
            throw new IllegalArgumentException("Invalid distant wildlife transition buffer: " + transitionBuffer);
        }
        if (distantWildlifeDistance < realEntityDistance + transitionBuffer
                || distantWildlifeDistance > 2_048) {
            throw new IllegalArgumentException("Invalid distant wildlife distance: " + distantWildlifeDistance);
        }
        if (updateInterval < 20 || updateInterval > 1_200) {
            throw new IllegalArgumentException("Invalid distant wildlife update interval: " + updateInterval);
        }
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    /** Immutable wire representation of one group-level population. */
    public record GroupSnapshot(
            long id,
            ResourceLocation species,
            int populationEstimate,
            double anchorX,
            double anchorY,
            double anchorZ,
            float directionX,
            float directionZ,
            float speed,
            long seed,
            long referenceGameTime,
            DistantWildlifeForm form
    ) {
        public GroupSnapshot {
            if (id <= 0L) {
                throw new IllegalArgumentException("Distant wildlife group id must be positive");
            }
            species = Objects.requireNonNull(species, "species");
            if (populationEstimate <= 0
                    || populationEstimate > DistantWildlifeGroup.MAXIMUM_GROUP_POPULATION) {
                throw new IllegalArgumentException("Invalid distant wildlife population: " + populationEstimate);
            }
            if (!finiteCoordinate(anchorX) || !finiteCoordinate(anchorY) || !finiteCoordinate(anchorZ)) {
                throw new IllegalArgumentException("Distant wildlife anchor is not finite or in world bounds");
            }
            if (!Float.isFinite(directionX) || !Float.isFinite(directionZ)
                    || Math.hypot(directionX, directionZ) < 0.5
                    || Math.hypot(directionX, directionZ) > 1.5) {
                throw new IllegalArgumentException("Invalid distant wildlife direction");
            }
            if (!Float.isFinite(speed) || speed < 0.0F || speed > 8.0F) {
                throw new IllegalArgumentException("Invalid distant wildlife speed: " + speed);
            }
            if (referenceGameTime < 0L) {
                throw new IllegalArgumentException("Distant wildlife reference time cannot be negative");
            }
            form = Objects.requireNonNull(form, "form");
        }

        /** Copies a server-owned group without expanding its represented population. */
        public static GroupSnapshot fromGroup(DistantWildlifeGroup group) {
            return fromGroup(group, group.populationEstimate());
        }

        /** Copies a server group while applying an explicit visual population cap. */
        public static GroupSnapshot fromGroup(DistantWildlifeGroup group, int representedPopulation) {
            if (representedPopulation <= 0 || representedPopulation > group.populationEstimate()) {
                throw new IllegalArgumentException(
                        "Visual population must be within the server-owned group population"
                );
            }
            return new GroupSnapshot(
                    group.id(), group.species(), representedPopulation,
                    group.anchorX(), group.anchorY(), group.anchorZ(),
                    (float) group.directionX(), (float) group.directionZ(),
                    (float) group.speed(), group.seed(), group.referenceGameTime(), group.form()
            );
        }

        /** Extrapolates the group anchor from the server time contract. */
        public Vec3 positionAt(double gameTime) {
            double elapsedSeconds = Math.max(0.0, gameTime - referenceGameTime) / 20.0;
            return new Vec3(
                    anchorX + directionX * speed * elapsedSeconds,
                    anchorY,
                    anchorZ + directionZ * speed * elapsedSeconds
            );
        }

        private static boolean finiteCoordinate(double value) {
            return Double.isFinite(value) && Math.abs(value) <= 30_000_000.0;
        }
    }
}
