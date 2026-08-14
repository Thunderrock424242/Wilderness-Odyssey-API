package com.thunder.wildernessodysseyapi.developmentstudio.region;

import com.thunder.wildernessodysseyapi.developmentstudio.StudioText;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.AABB;

import java.util.Optional;

/**
 * Persisted, server-owned bounds for one Development Studio test area.
 *
 * <p>Clients may render these bounds, but all mutations resolve the region
 * again from world data instead of trusting coordinates sent over the wire.</p>
 */
public record StudioTestRegion(
        ResourceLocation id,
        String displayName,
        ResourceLocation dimension,
        BlockPos min,
        BlockPos max,
        StudioTestRegionType type,
        StudioResetPolicy resetPolicy
) {
    public static final int MAX_DISPLAY_NAME = 48;

    public StudioTestRegion {
        if (id == null || dimension == null || min == null || max == null || type == null || resetPolicy == null) {
            throw new IllegalArgumentException("Studio test region fields cannot be null");
        }
        displayName = StudioText.singleLine(displayName, MAX_DISPLAY_NAME);
        BlockPos suppliedMin = min;
        BlockPos suppliedMax = max;
        min = new BlockPos(
                Math.min(suppliedMin.getX(), suppliedMax.getX()),
                Math.min(suppliedMin.getY(), suppliedMax.getY()),
                Math.min(suppliedMin.getZ(), suppliedMax.getZ())
        );
        max = new BlockPos(
                Math.max(suppliedMin.getX(), suppliedMax.getX()),
                Math.max(suppliedMin.getY(), suppliedMax.getY()),
                Math.max(suppliedMin.getZ(), suppliedMax.getZ())
        );
    }

    /** Returns the inclusive block count guarded by the region definition. */
    public long volume() {
        return (long) (max.getX() - min.getX() + 1)
                * (max.getY() - min.getY() + 1)
                * (max.getZ() - min.getZ() + 1);
    }

    public boolean contains(BlockPos position) {
        return position.getX() >= min.getX() && position.getX() <= max.getX()
                && position.getY() >= min.getY() && position.getY() <= max.getY()
                && position.getZ() >= min.getZ() && position.getZ() <= max.getZ();
    }

    /** Converts inclusive block coordinates into an outline-ready world box. */
    public AABB bounds() {
        return new AABB(min.getX(), min.getY(), min.getZ(),
                max.getX() + 1.0D, max.getY() + 1.0D, max.getZ() + 1.0D);
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putString("id", id.toString());
        tag.putString("display_name", displayName);
        tag.putString("dimension", dimension.toString());
        tag.putLong("min", min.asLong());
        tag.putLong("max", max.asLong());
        tag.putString("type", type.name());
        tag.putString("reset_policy", resetPolicy.name());
        return tag;
    }

    /** Reads one region while rejecting malformed ids, enums, and oversized bounds. */
    public static Optional<StudioTestRegion> load(CompoundTag tag) {
        ResourceLocation id = ResourceLocation.tryParse(tag.getString("id"));
        ResourceLocation dimension = ResourceLocation.tryParse(tag.getString("dimension"));
        if (id == null || dimension == null) {
            return Optional.empty();
        }
        try {
            StudioTestRegion region = new StudioTestRegion(
                    id,
                    tag.getString("display_name"),
                    dimension,
                    BlockPos.of(tag.getLong("min")),
                    BlockPos.of(tag.getLong("max")),
                    StudioTestRegionType.valueOf(tag.getString("type")),
                    StudioResetPolicy.valueOf(tag.getString("reset_policy"))
            );
            return region.volume() <= StudioTestRegionRegistry.MAX_REGION_VOLUME
                    ? Optional.of(region)
                    : Optional.empty();
        } catch (IllegalArgumentException exception) {
            return Optional.empty();
        }
    }
}
