package com.thunder.wildernessodysseyapi.util;

import com.thunder.wildernessodysseyapi.Core.ModConstants;
import com.thunder.wildernessodysseyapi.io.BufferPool;
import com.thunder.wildernessodysseyapi.io.CompressionCodec;
import com.thunder.wildernessodysseyapi.io.IoExecutors;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.EnumSet;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;
import net.jpountz.lz4.LZ4BlockInputStream;
import net.jpountz.lz4.LZ4BlockOutputStream;
import com.github.luben.zstd.ZstdInputStream;
import com.github.luben.zstd.ZstdOutputStream;

/**
 * Helpers for writing and rewriting NBT with configurable compression settings.
 */
public final class NbtCompressionUtils {

    private static final EnumSet<CompressionCodec> MISSING_CODEC_WARNED = EnumSet.noneOf(CompressionCodec.class);

    private NbtCompressionUtils() {
    }

    /**
     * Reads a compressed NBT file from disk without any size accounting limits.
     */
    public static CompoundTag readCompressed(Path source, CompressionCodec codec) throws IOException {
        try (InputStream fileStream = Files.newInputStream(source)) {
            if (codec == CompressionCodec.VANILLA_GZIP) {
                return NbtIo.readCompressed(fileStream, NbtAccounter.unlimitedHeap());
            }
            try (InputStream decodedStream = wrapDecompressor(codec, fileStream);
                 BufferPool.BufferSlice<byte[]> slice = BufferPool.heapSlice();
                 BufferPool.PooledByteArrayOutputStream copy = BufferPool.byteArrayOutputStream()) {
                copyStream(decodedStream, copy, slice.buffer());
                try (DataInputStream in = new DataInputStream(new ByteArrayInputStream(copy.directBuffer(), 0, copy.currentSize()))) {
                    return NbtIo.read(in, NbtAccounter.unlimitedHeap());
                }
            }
        }
    }

    public static CompoundTag readCompressed(Path source) throws IOException {
        return readCompressed(source, CompressionCodec.VANILLA_GZIP);
    }

    /**
     * Writes the NBT payload with a custom GZIP compression level.
     */
    public static void writeCompressed(Path target, CompoundTag tag, int compressionLevel, CompressionCodec codec) throws IOException {
        Files.createDirectories(target.getParent());
        if (codec == CompressionCodec.VANILLA_GZIP) {
            try (OutputStream fileStream = Files.newOutputStream(target);
                 GZIPOutputStream gzip = new GZIPOutputStream(fileStream) {
                     {
                         this.def.setLevel(compressionLevel);
                     }
                 };
                 DataOutputStream dataOutputStream = new DataOutputStream(gzip)) {
                NbtIo.write(tag, dataOutputStream);
                dataOutputStream.flush();
            }
            return;
        }

        try (OutputStream fileStream = Files.newOutputStream(target);
             OutputStream compressor = wrapCompressor(codec, fileStream, compressionLevel);
             BufferPool.PooledByteArrayOutputStream nbtBuffer = BufferPool.byteArrayOutputStream();
             DataOutputStream nbtOut = new DataOutputStream(nbtBuffer)) {
            NbtIo.write(tag, nbtOut);
            nbtOut.flush();
            compressor.write(nbtBuffer.directBuffer(), 0, nbtBuffer.currentSize());
        }
    }

    public static void writeCompressed(Path target, CompoundTag tag, int compressionLevel) throws IOException {
        writeCompressed(target, tag, compressionLevel, CompressionCodec.VANILLA_GZIP);
    }

    /**
     * Rewrites an existing compressed NBT payload with the provided compression level.
     * Logs failures at debug level so a bad structure file doesn't break saving.
     */
    public static void rewriteCompressed(Path target, int compressionLevel) {
        rewriteCompressed(target, compressionLevel, CompressionCodec.VANILLA_GZIP);
    }

    public static void rewriteCompressed(Path target, int compressionLevel, CompressionCodec codec) {
        try {
            CompoundTag tag = readCompressed(target, codec);
            writeCompressed(target, tag, compressionLevel, codec);
        } catch (IOException e) {
            ModConstants.LOGGER.debug("Failed to recompress NBT file {}", target, e);
        }
    }

    public static void rewriteCompressedAsync(Path target, int compressionLevel, CompressionCodec codec) {
        IoExecutors.submit(null, "nbt-rewrite-" + target.getFileName(), () -> rewriteCompressed(target, compressionLevel, codec));
    }

    private static InputStream wrapDecompressor(CompressionCodec codec, InputStream inputStream) throws IOException {
        try {
            return switch (codec) {
                case VANILLA_GZIP -> new GZIPInputStream(inputStream);
                case ZSTD -> new ZstdInputStream(inputStream);
                case LZ4 -> new LZ4BlockInputStream(inputStream);
            };
        } catch (NoClassDefFoundError e) {
            return fallbackDecompressor(codec, inputStream, e);
        }
    }

    private static OutputStream wrapCompressor(CompressionCodec codec, OutputStream target, int compressionLevel) throws IOException {
        try {
            return switch (codec) {
                case VANILLA_GZIP -> new GZIPOutputStream(target) {
                    {
                        this.def.setLevel(compressionLevel);
                    }
                };
                case ZSTD -> {
                    ZstdOutputStream stream = new ZstdOutputStream(target);
                    stream.setLevel(compressionLevel);
                    yield stream;
                }
                case LZ4 -> new LZ4BlockOutputStream(target);
            };
        } catch (NoClassDefFoundError e) {
            return fallbackCompressor(codec, target, compressionLevel, e);
        }
    }

    private static InputStream fallbackDecompressor(CompressionCodec codec, InputStream inputStream, NoClassDefFoundError missingCodec) throws IOException {
        logMissingCodec(codec, missingCodec);
        return new GZIPInputStream(inputStream);
    }

    private static OutputStream fallbackCompressor(CompressionCodec codec, OutputStream target, int compressionLevel, NoClassDefFoundError missingCodec) throws IOException {
        logMissingCodec(codec, missingCodec);
        return new GZIPOutputStream(target) {
            {
                this.def.setLevel(compressionLevel);
            }
        };
    }

    private static void logMissingCodec(CompressionCodec codec, NoClassDefFoundError missingCodec) {
        if (codec == CompressionCodec.VANILLA_GZIP) {
            throw missingCodec;
        }
        synchronized (MISSING_CODEC_WARNED) {
            if (MISSING_CODEC_WARNED.add(codec)) {
                ModConstants.LOGGER.warn("Missing {} codec dependency on the runtime classpath; falling back to vanilla GZIP", codec, missingCodec);
            }
        }
    }

    private static void copyStream(InputStream in, OutputStream out, byte[] buffer) throws IOException {
        int read;
        while ((read = in.read(buffer)) != -1) {
            out.write(buffer, 0, read);
        }
    }
}
