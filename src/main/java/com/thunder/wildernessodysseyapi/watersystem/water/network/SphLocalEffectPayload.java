package com.thunder.wildernessodysseyapi.watersystem.water.network;

import com.thunder.wildernessodysseyapi.core.ModConstants;
import com.thunder.wildernessodysseyapi.watersystem.water.sph.SPHConstants;
import com.thunder.wildernessodysseyapi.watersystem.water.sph.SPHSimulationManager;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Describes one local SPH visual effect without synchronizing every particle.
 *
 * <p>The server sends compact event parameters such as position, impulse,
 * particle request, and lifetime. Each receiving client applies its own SPH
 * quality settings and simulates the short-lived visual body locally. This keeps
 * SPH out of permanent water storage and avoids particle-by-particle network
 * traffic for splashes, shore wash, and bucket effects.</p>
 */
public record SphLocalEffectPayload(
        int effectType,
        float x,
        float y,
        float z,
        float impulseX,
        float impulseY,
        float impulseZ,
        int requestedParticles,
        int requestedLifetimeTicks
) implements CustomPacketPayload {

    public static final int EFFECT_BUCKET_SPLASH = 0;
    public static final int EFFECT_SHORE_WASH = 1;
    public static final double TRACKING_DISTANCE = 64.0;

    public static final Type<SphLocalEffectPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(ModConstants.MOD_ID, "sph_local_effect")
    );

    public static final StreamCodec<FriendlyByteBuf, SphLocalEffectPayload> STREAM_CODEC =
            StreamCodec.of(SphLocalEffectPayload::encode, SphLocalEffectPayload::decode);

    public SphLocalEffectPayload {
        effectType = Math.max(0, effectType);
        x = finiteOrZero(x);
        y = finiteOrZero(y);
        z = finiteOrZero(z);
        impulseX = finiteOrZero(impulseX);
        impulseY = finiteOrZero(impulseY);
        impulseZ = finiteOrZero(impulseZ);
        requestedParticles = Math.max(0, Math.min(SPHConstants.MAX_PARTICLES, requestedParticles));
        requestedLifetimeTicks = Math.max(0, Math.min(200, requestedLifetimeTicks));
    }

    /** Creates the visual effect fired after a bucket becomes Wilderness water. */
    public static SphLocalEffectPayload bucketSplash(float x, float y, float z) {
        return new SphLocalEffectPayload(
                EFFECT_BUCKET_SPLASH,
                x,
                y,
                z,
                0.0f,
                0.0f,
                0.0f,
                SPHConstants.BUCKET_SPLASH_PARTICLES,
                SPHConstants.BUCKET_SPLASH_LIFETIME_TICKS
        );
    }

    /** Creates the visual wash effect fired by shore/tide events. */
    public static SphLocalEffectPayload shoreWash(
            float x,
            float y,
            float z,
            int particles,
            float impulseX,
            float impulseY,
            float impulseZ,
            int lifetimeTicks
    ) {
        return new SphLocalEffectPayload(
                EFFECT_SHORE_WASH,
                x,
                y,
                z,
                impulseX,
                impulseY,
                impulseZ,
                particles,
                lifetimeTicks
        );
    }

    /** Sends one local SPH event to players close enough to see it. */
    public static void sendToNearby(ServerLevel level, double x, double y, double z, SphLocalEffectPayload payload) {
        double maxDistanceSquared = TRACKING_DISTANCE * TRACKING_DISTANCE;
        for (ServerPlayer player : level.players()) {
            double dx = player.getX() - x;
            double dy = player.getY() - y;
            double dz = player.getZ() - z;
            if (dx * dx + dy * dy + dz * dz <= maxDistanceSquared) {
                PacketDistributor.sendToPlayer(player, payload);
            }
        }
    }

    /** Spawns the short-lived client-owned SPH body for this effect. */
    public void spawnClientEffect(net.minecraft.world.level.BlockGetter level) {
        SPHSimulationManager.get().createLocalVisualEffect(
                x,
                y,
                z,
                level,
                requestedParticles,
                impulseX,
                impulseY,
                impulseZ,
                requestedLifetimeTicks
        );
    }

    private static void encode(FriendlyByteBuf buffer, SphLocalEffectPayload payload) {
        buffer.writeVarInt(payload.effectType);
        buffer.writeFloat(payload.x);
        buffer.writeFloat(payload.y);
        buffer.writeFloat(payload.z);
        buffer.writeFloat(payload.impulseX);
        buffer.writeFloat(payload.impulseY);
        buffer.writeFloat(payload.impulseZ);
        buffer.writeVarInt(payload.requestedParticles);
        buffer.writeVarInt(payload.requestedLifetimeTicks);
    }

    private static SphLocalEffectPayload decode(FriendlyByteBuf buffer) {
        return new SphLocalEffectPayload(
                buffer.readVarInt(),
                buffer.readFloat(),
                buffer.readFloat(),
                buffer.readFloat(),
                buffer.readFloat(),
                buffer.readFloat(),
                buffer.readFloat(),
                buffer.readVarInt(),
                buffer.readVarInt()
        );
    }

    private static float finiteOrZero(float value) {
        return Float.isFinite(value) ? value : 0.0f;
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
