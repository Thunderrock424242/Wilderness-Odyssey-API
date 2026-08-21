package com.thunder.wildernessodysseyapi.util;

import com.thunder.wildernessodysseyapi.core.ModConstants;
import com.thunder.wildernessodysseyapi.io.CompressionCodec;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtIo;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.EnumSet;
import java.util.zip.GZIPOutputStream;

/**
 * Writes already-validated NBT with configurable compression settings.
 */
public final class NbtCompressionUtils {

    private static final EnumSet<CompressionCodec> MISSING_CODEC_WARNED = EnumSet.noneOf(CompressionCodec.class);

    private NbtCompressionUtils() {
    }

    /**
     * Writes the NBT payload with a custom GZIP compression level.
     */
    public static void writeCompressed(Path target, CompoundTag tag, int compressionLevel, CompressionCodec codec) throws IOException {
        Path parent = target.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
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
             DataOutputStream nbtOut = new DataOutputStream(compressor)) {
            NbtIo.write(tag, nbtOut);
            nbtOut.flush();
        }
    }

    public static void writeCompressed(Path target, CompoundTag tag, int compressionLevel) throws IOException {
        writeCompressed(target, tag, compressionLevel, CompressionCodec.VANILLA_GZIP);
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

    private static OutputStream createZstdOutputStream(OutputStream target, int compressionLevel) throws IOException {
        OutputStream stream = createOptionalOutputStream("com.github.luben.zstd.ZstdOutputStream", target);
        try {
            stream.getClass().getMethod("setLevel", int.class).invoke(stream, compressionLevel);
            return stream;
        } catch (ReflectiveOperationException e) {
            throw new IOException("Failed to configure Zstandard compression level", e);
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
