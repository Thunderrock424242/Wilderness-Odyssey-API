package com.thunder.wildernessodysseyapi.temporalrift;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;

public class TemporalEcho {
    public enum Type {
        PLACE,
        BREAK
    }

    private final BlockPos sourcePos;
    private final long revealDay;
    private final String materialKey;
    private final String playerName;
    private final Type type;

    public TemporalEcho(BlockPos sourcePos, long revealDay, String materialKey, String playerName, Type type) {
        this.sourcePos = sourcePos.immutable();
        this.revealDay = revealDay;
        this.materialKey = materialKey;
        this.playerName = playerName;
        this.type = type;
    }

    public BlockPos getSourcePos() {
        return sourcePos;
    }

    public long getRevealDay() {
        return revealDay;
    }

    public String getMaterialKey() {
        return materialKey;
    }

    public String getPlayerName() {
        return playerName;
    }

    public Type getType() {
        return type;
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putInt("x", sourcePos.getX());
        tag.putInt("y", sourcePos.getY());
        tag.putInt("z", sourcePos.getZ());
        tag.putLong("revealDay", revealDay);
        tag.putString("materialKey", materialKey);
        tag.putString("playerName", playerName);
        tag.putString("type", type.name());
        return tag;
    }

    public static TemporalEcho load(CompoundTag tag) {
        return new TemporalEcho(
                new BlockPos(tag.getInt("x"), tag.getInt("y"), tag.getInt("z")),
                tag.getLong("revealDay"),
                tag.getString("materialKey"),
                tag.getString("playerName"),
                loadType(tag)
        );
    }

    private static Type loadType(CompoundTag tag) {
        if (!tag.contains("type")) {
            return Type.PLACE;
        }
        try {
            return Type.valueOf(tag.getString("type"));
        } catch (IllegalArgumentException ignored) {
            return Type.PLACE;
        }
    }
}
