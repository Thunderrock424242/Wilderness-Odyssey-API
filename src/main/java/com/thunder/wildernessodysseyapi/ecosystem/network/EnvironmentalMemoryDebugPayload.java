package com.thunder.wildernessodysseyapi.ecosystem.network;

import com.thunder.wildernessodysseyapi.core.ModConstants;
import com.thunder.wildernessodysseyapi.ecosystem.memory.EnvironmentalMemory;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.ChunkPos;

import java.util.Optional;

/**
 * Debug-only server snapshot for the existing categorized F3 World page.
 *
 * <p>Only the player's current cell is synchronized, at most once per second,
 * and only while ecosystem diagnostics are enabled on the server.</p>
 */
public record EnvironmentalMemoryDebugPayload(
        ResourceLocation dimension,
        int chunkX,
        int chunkZ,
        boolean present,
        float disturbance,
        float fireActivity,
        float combatActivity,
        float playerTraffic,
        long lastUpdatedGameTime,
        long observedGameTime,
        float decayApplied,
        String lastSource,
        int sourceX,
        int sourceY,
        int sourceZ,
        int activeCellCount
) implements CustomPacketPayload {

    /** Payload identifier used by NeoForge's play protocol. */
    public static final Type<EnvironmentalMemoryDebugPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(ModConstants.MOD_ID, "environmental_memory_debug")
    );
    /** Bounded codec for one current-cell diagnostic snapshot. */
    public static final StreamCodec<FriendlyByteBuf, EnvironmentalMemoryDebugPayload> STREAM_CODEC =
            StreamCodec.of(EnvironmentalMemoryDebugPayload::encode, EnvironmentalMemoryDebugPayload::decode);

    public EnvironmentalMemoryDebugPayload {
        if (dimension == null) {
            throw new IllegalArgumentException("Environmental-memory debug dimension is required");
        }
        disturbance = unit(disturbance);
        fireActivity = unit(fireActivity);
        combatActivity = unit(combatActivity);
        playerTraffic = unit(playerTraffic);
        decayApplied = Math.max(0.0F, finite(decayApplied));
        lastSource = lastSource == null ? "other" : lastSource.substring(0, Math.min(32, lastSource.length()));
        activeCellCount = Math.max(0, activeCellCount);
    }

    /** Creates a packet from an optional current-cell server snapshot. */
    public static EnvironmentalMemoryDebugPayload create(
            ResourceLocation dimension,
            ChunkPos cell,
            Optional<EnvironmentalMemory> memory,
            long gameTime,
            int activeCellCount
    ) {
        if (memory.isEmpty()) {
            return new EnvironmentalMemoryDebugPayload(
                    dimension, cell.x, cell.z, false,
                    0.0F, 0.0F, 0.0F, 0.0F,
                    gameTime, gameTime, 0.0F, "none",
                    cell.getMiddleBlockX(), 0, cell.getMiddleBlockZ(), activeCellCount
            );
        }
        EnvironmentalMemory snapshot = memory.get();
        return new EnvironmentalMemoryDebugPayload(
                dimension,
                cell.x,
                cell.z,
                true,
                (float) snapshot.disturbance(),
                (float) snapshot.recentFireActivity(),
                (float) snapshot.recentCombatActivity(),
                (float) snapshot.playerTraffic(),
                snapshot.lastUpdatedGameTime(),
                snapshot.observedGameTime(),
                (float) snapshot.disturbanceDecayApplied(),
                snapshot.lastSource().serializedName(),
                snapshot.lastSourcePosition().getX(),
                snapshot.lastSourcePosition().getY(),
                snapshot.lastSourcePosition().getZ(),
                activeCellCount
        );
    }

    /** Returns the non-negative lazy-decay interval shown by the debug HUD. */
    public long elapsedTicks() {
        return Math.max(0L, observedGameTime - lastUpdatedGameTime);
    }

    private static void encode(FriendlyByteBuf buffer, EnvironmentalMemoryDebugPayload payload) {
        buffer.writeResourceLocation(payload.dimension);
        buffer.writeInt(payload.chunkX);
        buffer.writeInt(payload.chunkZ);
        buffer.writeBoolean(payload.present);
        buffer.writeFloat(payload.disturbance);
        buffer.writeFloat(payload.fireActivity);
        buffer.writeFloat(payload.combatActivity);
        buffer.writeFloat(payload.playerTraffic);
        buffer.writeLong(payload.lastUpdatedGameTime);
        buffer.writeLong(payload.observedGameTime);
        buffer.writeFloat(payload.decayApplied);
        buffer.writeUtf(payload.lastSource, 32);
        buffer.writeInt(payload.sourceX);
        buffer.writeInt(payload.sourceY);
        buffer.writeInt(payload.sourceZ);
        buffer.writeVarInt(payload.activeCellCount);
    }

    private static EnvironmentalMemoryDebugPayload decode(FriendlyByteBuf buffer) {
        return new EnvironmentalMemoryDebugPayload(
                buffer.readResourceLocation(),
                buffer.readInt(),
                buffer.readInt(),
                buffer.readBoolean(),
                buffer.readFloat(),
                buffer.readFloat(),
                buffer.readFloat(),
                buffer.readFloat(),
                buffer.readLong(),
                buffer.readLong(),
                buffer.readFloat(),
                buffer.readUtf(32),
                buffer.readInt(),
                buffer.readInt(),
                buffer.readInt(),
                buffer.readVarInt()
        );
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    private static float unit(float value) {
        return Math.max(0.0F, Math.min(1.0F, finite(value)));
    }

    private static float finite(float value) {
        return Float.isFinite(value) ? value : 0.0F;
    }
}
