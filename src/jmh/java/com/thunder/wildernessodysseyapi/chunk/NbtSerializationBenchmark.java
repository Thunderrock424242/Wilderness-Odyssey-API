package com.thunder.wildernessodysseyapi.chunk;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.world.level.ChunkPos;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.concurrent.TimeUnit;
import java.util.zip.GZIPOutputStream;

@State(Scope.Benchmark)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
public class NbtSerializationBenchmark {

    @Param({"3", "6", "9"})
    public int compressionLevel;

    private CompoundTag chunkTag;
    private byte[] serialized;

    @Setup(Level.Trial)
    public void setUp() throws IOException {
        chunkTag = ChunkNbtFixtures.sampleChunk(new ChunkPos(4, -4), 42, 6);
        serialized = writeCompressed(chunkTag, compressionLevel);
    }

    @Benchmark
    @BenchmarkMode(Mode.SampleTime)
    public int measureWriteSize() throws IOException {
        return writeCompressed(chunkTag, compressionLevel).length;
    }

    @Benchmark
    @BenchmarkMode(Mode.AverageTime)
    public CompoundTag measureReadCompressed() throws IOException {
        return readCompressed(serialized);
    }

    private static byte[] writeCompressed(CompoundTag tag, int level) throws IOException {
        try (ByteArrayOutputStream buffer = new ByteArrayOutputStream();
             GZIPOutputStream gzip = new GZIPOutputStream(buffer) {
                 {
                     this.def.setLevel(level);
                 }
             };
             DataOutputStream dataOutputStream = new DataOutputStream(gzip)) {
            NbtIo.write(tag, dataOutputStream);
            gzip.finish();
            return buffer.toByteArray();
        }
    }

    private static CompoundTag readCompressed(byte[] payload) throws IOException {
        try (ByteArrayInputStream inputStream = new ByteArrayInputStream(payload)) {
            return NbtIo.readCompressed(inputStream, NbtAccounter.unlimitedHeap());
        }
    }
}
