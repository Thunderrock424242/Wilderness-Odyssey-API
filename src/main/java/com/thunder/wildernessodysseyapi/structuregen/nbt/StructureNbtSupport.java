package com.thunder.wildernessodysseyapi.structuregen.nbt;

import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.DoubleTag;
import net.minecraft.nbt.IntTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.TagParser;

final class StructureNbtSupport {

    private StructureNbtSupport() {
    }

    static CompoundTag parseCompound(String snbt, String description) {
        if (snbt == null) {
            return new CompoundTag();
        }
        try {
            return TagParser.parseTag(snbt);
        } catch (CommandSyntaxException exception) {
            throw new IllegalArgumentException("Malformed " + description + " SNBT: " + exception.getMessage(), exception);
        }
    }

    static ListTag integerList(int x, int y, int z) {
        ListTag values = new ListTag();
        values.add(IntTag.valueOf(x));
        values.add(IntTag.valueOf(y));
        values.add(IntTag.valueOf(z));
        return values;
    }

    static ListTag doubleList(double x, double y, double z) {
        ListTag values = new ListTag();
        values.add(DoubleTag.valueOf(x));
        values.add(DoubleTag.valueOf(y));
        values.add(DoubleTag.valueOf(z));
        return values;
    }

    static ListTag stringList(Iterable<String> strings) {
        ListTag values = new ListTag();
        strings.forEach(value -> values.add(StringTag.valueOf(value)));
        return values;
    }
}
