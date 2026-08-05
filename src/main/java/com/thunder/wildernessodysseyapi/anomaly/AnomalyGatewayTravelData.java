package com.thunder.wildernessodysseyapi.anomaly;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;

/**
 * Stores the dimension-aware return link created by an Anomaly Gateway.
 *
 * <p>The coordinate keys intentionally retain their original names so players
 * who entered the dimension in an older build still return to their saved
 * Overworld gateway. New saves also record The Before as a valid origin.</p>
 */
public final class AnomalyGatewayTravelData {
    private static final String NBT_RETURN_DIMENSION = "anomaly_gateway_return_dimension";
    private static final String NBT_RETURN_X = "anomaly_gateway_return_x";
    private static final String NBT_RETURN_Y = "anomaly_gateway_return_y";
    private static final String NBT_RETURN_Z = "anomaly_gateway_return_z";

    private AnomalyGatewayTravelData() {
    }

    /** Stores the exact source gateway and its dimension on persistent player data. */
    public static void store(CompoundTag data, ResourceKey<Level> dimension, BlockPos gatewayPos) {
        data.putString(NBT_RETURN_DIMENSION, dimension.location().toString());
        data.putInt(NBT_RETURN_X, gatewayPos.getX());
        data.putInt(NBT_RETURN_Y, gatewayPos.getY());
        data.putInt(NBT_RETURN_Z, gatewayPos.getZ());
    }

    /**
     * Reads a validated return link, falling back safely for legacy or malformed data.
     */
    public static ReturnTarget read(
            CompoundTag data,
            ResourceKey<Level> fallbackDimension,
            BlockPos fallbackPosition
    ) {
        ResourceKey<Level> dimension = readDimension(data, fallbackDimension);
        int x = data.contains(NBT_RETURN_X) ? data.getInt(NBT_RETURN_X) : fallbackPosition.getX();
        int y = data.contains(NBT_RETURN_Y) ? data.getInt(NBT_RETURN_Y) : fallbackPosition.getY();
        int z = data.contains(NBT_RETURN_Z) ? data.getInt(NBT_RETURN_Z) : fallbackPosition.getZ();
        return new ReturnTarget(dimension, new BlockPos(x, y, z));
    }

    /** Clears a consumed return link only after the destination was resolved. */
    public static void clear(CompoundTag data) {
        data.remove(NBT_RETURN_DIMENSION);
        data.remove(NBT_RETURN_X);
        data.remove(NBT_RETURN_Y);
        data.remove(NBT_RETURN_Z);
    }

    private static ResourceKey<Level> readDimension(CompoundTag data, ResourceKey<Level> fallbackDimension) {
        ResourceLocation id = ResourceLocation.tryParse(data.getString(NBT_RETURN_DIMENSION));
        if (id == null) {
            return fallbackDimension;
        }

        ResourceKey<Level> dimension = ResourceKey.create(Registries.DIMENSION, id);
        return AnomalyDimensionRules.isGatewaySource(dimension) ? dimension : fallbackDimension;
    }

    /** A source dimension and the gateway block position within it. */
    public record ReturnTarget(ResourceKey<Level> dimension, BlockPos gatewayPos) {
    }
}
