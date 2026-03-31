package com.thunder.wildernessodysseyapi.watersystem.volumetric.network;

import com.thunder.wildernessodysseyapi.core.ModConstants;
import com.thunder.wildernessodysseyapi.watersystem.volumetric.VolumetricFluidManager.SimulatedFluid;
import com.thunder.wildernessodysseyapi.watersystem.volumetric.VolumetricFluidManager.SurfaceSample;
import com.thunder.wildernessodysseyapi.watersystem.volumetric.client.VolumetricSurfaceClientCache;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.ArrayList;
import java.util.List;

/**
 * Server -> client payload containing sampled surfaces for water/lava mesh reconstruction.
 */
public record VolumetricSurfaceSyncPayload(
        ResourceLocation dimensionId,
        long gameTime,
        List<SurfaceSample> waterSamples,
        List<SurfaceSample> lavaSamples
) implements CustomPacketPayload {

    public static final Type<VolumetricSurfaceSyncPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(ModConstants.MOD_ID, "volumetric_surface_sync")
    );

    public static final StreamCodec<FriendlyByteBuf, VolumetricSurfaceSyncPayload> STREAM_CODEC = StreamCodec.of(
            VolumetricSurfaceSyncPayload::encode,
            VolumetricSurfaceSyncPayload::decode
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    private static void encode(FriendlyByteBuf buf, VolumetricSurfaceSyncPayload payload) {
        buf.writeResourceLocation(payload.dimensionId);
        buf.writeVarLong(payload.gameTime);
        writeSamples(buf, payload.waterSamples);
        writeSamples(buf, payload.lavaSamples);
    }

    private static VolumetricSurfaceSyncPayload decode(FriendlyByteBuf buf) {
        ResourceLocation dimensionId = buf.readResourceLocation();
        long gameTime = buf.readVarLong();
        List<SurfaceSample> water = readSamples(buf, SimulatedFluid.WATER);
        List<SurfaceSample> lava = readSamples(buf, SimulatedFluid.LAVA);
        return new VolumetricSurfaceSyncPayload(dimensionId, gameTime, water, lava);
    }

    private static void writeSamples(FriendlyByteBuf buf, List<SurfaceSample> samples) {
        buf.writeVarInt(samples.size());
        for (SurfaceSample sample : samples) {
            buf.writeVarInt(sample.blockX());
            buf.writeVarInt(sample.blockZ());
            buf.writeFloat((float) sample.surfaceY());
            buf.writeFloat((float) sample.volume());
            buf.writeFloat((float) sample.shorelineFactor());
            buf.writeFloat((float) sample.tideOffset());
            buf.writeFloat((float) sample.moonPhaseFactor());
        }
    }

    private static List<SurfaceSample> readSamples(FriendlyByteBuf buf, SimulatedFluid fluidType) {
        int size = Math.max(0, buf.readVarInt());
        List<SurfaceSample> samples = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            int x = buf.readVarInt();
            int z = buf.readVarInt();
            double y = buf.readFloat();
            double volume = buf.readFloat();
            double shorelineFactor = buf.readFloat();
            double tideOffset = buf.readFloat();
            double moonPhaseFactor = buf.readFloat();
            samples.add(new SurfaceSample(x, z, y, volume, shorelineFactor, tideOffset, moonPhaseFactor, fluidType));
        }
        return samples;
    }

    public static void handle(VolumetricSurfaceSyncPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player().level().dimension().location().equals(payload.dimensionId)) {
                VolumetricSurfaceClientCache.replace(payload.dimensionId, SimulatedFluid.WATER, payload.waterSamples, payload.gameTime);
                VolumetricSurfaceClientCache.replace(payload.dimensionId, SimulatedFluid.LAVA, payload.lavaSamples, payload.gameTime);
            }
        });
    }
}
