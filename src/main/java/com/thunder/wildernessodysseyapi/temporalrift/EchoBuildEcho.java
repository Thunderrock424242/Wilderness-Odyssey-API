package com.thunder.wildernessodysseyapi.temporalrift;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;

public class EchoBuildEcho {
    private final BlockPos sourcePos;
    private final BlockPos targetPos;
    private final long revealDay;
    private final String materialKey;
    private final String playerName;
    private final TemporalEcho.Type type;

    public EchoBuildEcho(BlockPos sourcePos, BlockPos targetPos, long revealDay, String materialKey, String playerName, TemporalEcho.Type type) {
        this.sourcePos = sourcePos.immutable();
        this.targetPos = targetPos.immutable();
        this.revealDay = revealDay;
        this.materialKey = materialKey;
        this.playerName = playerName;
        this.type = type;
    }

    public BlockPos sourcePos() {
        return sourcePos;
    }

    public BlockPos targetPos() {
        return targetPos;
    }

    public long revealDay() {
        return revealDay;
    }

    public String materialKey() {
        return materialKey;
    }

    public String playerName() {
        return playerName;
    }

    public TemporalEcho.Type type() {
        return type;
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putInt("sourceX", sourcePos.getX());
        tag.putInt("sourceY", sourcePos.getY());
        tag.putInt("sourceZ", sourcePos.getZ());
        tag.putInt("targetX", targetPos.getX());
        tag.putInt("targetY", targetPos.getY());
        tag.putInt("targetZ", targetPos.getZ());
        tag.putLong("revealDay", revealDay);
        tag.putString("materialKey", materialKey);
        tag.putString("playerName", playerName);
        tag.putString("type", type.name());
        return tag;
    }

    public static EchoBuildEcho load(CompoundTag tag) {
        return new EchoBuildEcho(
                new BlockPos(tag.getInt("sourceX"), tag.getInt("sourceY"), tag.getInt("sourceZ")),
                new BlockPos(tag.getInt("targetX"), tag.getInt("targetY"), tag.getInt("targetZ")),
                tag.getLong("revealDay"),
                tag.getString("materialKey"),
                tag.getString("playerName"),
                loadType(tag)
        );
    }

    private static TemporalEcho.Type loadType(CompoundTag tag) {
        if (!tag.contains("type")) {
            return TemporalEcho.Type.PLACE;
        }
        try {
            return TemporalEcho.Type.valueOf(tag.getString("type"));
        } catch (IllegalArgumentException ignored) {
            return TemporalEcho.Type.PLACE;
        }
    }
}
