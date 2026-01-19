package com.thunder.wildernessodysseyapi.util;

import com.thunder.wildernessodysseyapi.Core.ModConstants;
import com.thunder.wildernessodysseyapi.io.CompressionCodec;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.EnumSet;
import java.util.concurrent.CompletableFuture;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

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
            try (InputStream decodedStream = wrapDecompressor(codec, fileStream)) {
                ByteArrayOutputStream copy = new ByteArrayOutputStream();
                copyStream(decodedStream, copy, new byte[8192]);
                try (DataInputStream in = new DataInputStream(new ByteArrayInputStream(copy.toByteArray()))) {
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
             OutputStream compressor = wrapCompressor(codec, fileStream, compressionLevel)) {
            ByteArrayOutputStream nbtBuffer = new ByteArrayOutputStream();
            try (DataOutputStream nbtOut = new DataOutputStream(nbtBuffer)) {
                NbtIo.write(tag, nbtOut);
                nbtOut.flush();
            }
            compressor.write(nbtBuffer.toByteArray());
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
        CompletableFuture.runAsync(() -> rewriteCompressed(target, compressionLevel, codec));
    }

    private static InputStream wrapDecompressor(CompressionCodec codec, InputStream inputStream) throws IOException {
        try {
            return switch (codec) {
                case VANILLA_GZIP -> new GZIPInputStream(inputStream);
                case ZSTD -> createZstdInputStream(inputStream);
                case LZ4 -> createOptionalInputStream("net.jpountz.lz4.LZ4BlockInputStream", inputStream);
            };
        } catch (NoClassDefFoundError missingCodec) {
            return fallbackDecompressor(codec, inputStream, missingCodec);
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
                case ZSTD -> createZstdOutputStream(target, compressionLevel);
                case LZ4 -> createOptionalOutputStream("net.jpountz.lz4.LZ4BlockOutputStream", target);
            };
        } catch (NoClassDefFoundError missingCodec) {
            return fallbackCompressor(codec, target, compressionLevel, missingCodec);
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

    private static InputStream createZstdInputStream(InputStream source) throws IOException {
        return createOptionalInputStream("com.github.luben.zstd.ZstdInputStream", source);
    }

    private static OutputStream createZstdOutputStream(OutputStream target, int compressionLevel) throws IOException {
        OutputStream stream = createOptionalOutputStream("com.github.luben.zstd.ZstdOutputStream", target);
        try {
            stream.getClass().getMethod("setLevel", int.class).invoke(stream, compressionLevel);
            return stream;
        } catch (ReflectiveOperationException e) {
            throw new IOException("Failed to configure Zstandard compression level", e);
        }
    }

    private static InputStream createOptionalInputStream(String className, InputStream source) throws IOException {
        try {
            Class<?> codecClass = Class.forName(className);
            return InputStream.class.cast(codecClass.getConstructor(InputStream.class).newInstance(source));
        } catch (ClassNotFoundException | NoClassDefFoundError e) {
            throw missingCodec(className, e);
        } catch (ReflectiveOperationException e) {
            throw new IOException("Failed to initialize " + className, e);
        }
    }

    private static OutputStream createOptionalOutputStream(String className, OutputStream target) throws IOException {
        try {
            Class<?> codecClass = Class.forName(className);
            return OutputStream.class.cast(codecClass.getConstructor(OutputStream.class).newInstance(target));
        } catch (ClassNotFoundException | NoClassDefFoundError e) {
            throw missingCodec(className, e);
        } catch (ReflectiveOperationException e) {
            throw new IOException("Failed to initialize " + className, e);
        }
    }

    private static NoClassDefFoundError missingCodec(String className, Throwable cause) {
        NoClassDefFoundError error = new NoClassDefFoundError(className);
        error.initCause(cause);
        return error;
    }
}
