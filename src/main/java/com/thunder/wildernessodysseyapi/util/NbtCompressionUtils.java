package com.thunder.wildernessodysseyapi.util;

import com.thunder.wildernessodysseyapi.Core.ModConstants;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;

import java.io.BufferedOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.GZIPOutputStream;

/**
 * Helpers for writing and rewriting NBT with configurable compression settings.
 */
public final class NbtCompressionUtils {

    private NbtCompressionUtils() {
    }

    /**
     * Reads a compressed NBT file from disk without any size accounting limits.
     */
    public static CompoundTag readCompressed(Path source) throws IOException {
        try (InputStream inputStream = Files.newInputStream(source)) {
            return NbtIo.readCompressed(inputStream, NbtAccounter.unlimitedHeap());
        }
    }

    /**
     * Writes the NBT payload with a custom GZIP compression level.
     */
    public static void writeCompressed(Path target, CompoundTag tag, int compressionLevel) throws IOException {
        Files.createDirectories(target.getParent());
        try (OutputStream fileStream = Files.newOutputStream(target);
                BufferedOutputStream buffered = new BufferedOutputStream(fileStream);
                GZIPOutputStream gzip = new GZIPOutputStream(buffered) {
                    {
                        this.def.setLevel(compressionLevel);
                    }
                };
                DataOutputStream dataOutputStream = new DataOutputStream(gzip)) {
            NbtIo.write(tag, dataOutputStream);
        }
    }

    /**
     * Rewrites an existing compressed NBT payload with the provided compression level.
     * Logs failures at debug level so a bad structure file doesn't break saving.
     */
    public static void rewriteCompressed(Path target, int compressionLevel) {
        try {
            CompoundTag tag = readCompressed(target);
            writeCompressed(target, tag, compressionLevel);
        } catch (IOException e) {
            ModConstants.LOGGER.debug("Failed to recompress NBT file {}", target, e);
        }
    }
}
