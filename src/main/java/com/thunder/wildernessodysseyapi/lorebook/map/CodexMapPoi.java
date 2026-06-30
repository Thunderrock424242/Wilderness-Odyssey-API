package com.thunder.wildernessodysseyapi.lorebook.map;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;

/**
 * Server-authored point of interest shown in the Field Codex map and optionally
 * mirrored into BlueMap's web marker layer.
 *
 * @param id stable id for replacing/updating the marker
 * @param label player-facing marker label
 * @param type small category id such as {@code cryo} or {@code meteor}
 * @param dimension dimension resource id where the POI lives
 * @param x block x-coordinate
 * @param y block y-coordinate
 * @param z block z-coordinate
 * @param color ARGB color used by the in-book map marker
 */
public record CodexMapPoi(
        String id,
        String label,
        String type,
        ResourceLocation dimension,
        int x,
        int y,
        int z,
        int color
) {
    private static final int MAX_ID_LENGTH = 128;
    private static final int MAX_LABEL_LENGTH = 96;
    private static final int MAX_TYPE_LENGTH = 32;

    /** Creates a POI from a block position. */
    public static CodexMapPoi at(
            String id,
            String label,
            String type,
            ResourceLocation dimension,
            BlockPos pos,
            int color
    ) {
        return new CodexMapPoi(id, label, type, dimension, pos.getX(), pos.getY(), pos.getZ(), color);
    }

    /** Writes this POI to a play-channel buffer. */
    public void encode(FriendlyByteBuf buffer) {
        buffer.writeUtf(id == null ? "" : id, MAX_ID_LENGTH);
        buffer.writeUtf(label == null ? "" : label, MAX_LABEL_LENGTH);
        buffer.writeUtf(type == null ? "" : type, MAX_TYPE_LENGTH);
        buffer.writeResourceLocation(dimension);
        buffer.writeInt(x);
        buffer.writeInt(y);
        buffer.writeInt(z);
        buffer.writeInt(color);
    }

    /** Reads one POI from a play-channel buffer. */
    public static CodexMapPoi decode(FriendlyByteBuf buffer) {
        return new CodexMapPoi(
                buffer.readUtf(MAX_ID_LENGTH),
                buffer.readUtf(MAX_LABEL_LENGTH),
                buffer.readUtf(MAX_TYPE_LENGTH),
                buffer.readResourceLocation(),
                buffer.readInt(),
                buffer.readInt(),
                buffer.readInt(),
                buffer.readInt()
        );
    }

    /** Returns the marker position as a block position. */
    public BlockPos blockPos() {
        return new BlockPos(x, y, z);
    }
}
