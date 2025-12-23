package com.thunder.wildernessodysseyapi.io;

import com.thunder.wildernessodysseyapi.chunk.ChunkStreamingConfig;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Lightweight thread-local arenas for reusable byte and mesh buffers.
 */
public final class BufferPool {

    private static final ThreadLocal<Deque<byte[]>> HEAP_SLICES = ThreadLocal.withInitial(ArrayDeque::new);
    private static final ThreadLocal<Deque<ByteBuffer>> DIRECT_SLICES = ThreadLocal.withInitial(ArrayDeque::new);
    private static final AtomicInteger CONFIGURED_SLICE_BYTES = new AtomicInteger(16 * 1024);
    private static final AtomicInteger CONFIGURED_SLICES_PER_THREAD = new AtomicInteger(8);

    private BufferPool() {
    }

    public static void configure(ChunkStreamingConfig.ChunkConfigValues config) {
        Objects.requireNonNull(config, "config");
        CONFIGURED_SLICE_BYTES.set(Math.max(1024, config.bufferSliceBytes()));
        CONFIGURED_SLICES_PER_THREAD.set(Math.max(1, config.bufferSlicesPerThread()));
    }

    public static BufferSlice<byte[]> heapSlice() {
        return heapSlice(CONFIGURED_SLICE_BYTES.get());
    }

    public static BufferSlice<byte[]> heapSlice(int minimumSize) {
        int sliceSize = Math.max(minimumSize, CONFIGURED_SLICE_BYTES.get());
        Deque<byte[]> arena = HEAP_SLICES.get();
        byte[] buffer = arena.pollFirst();
        if (buffer == null || buffer.length < sliceSize) {
            buffer = new byte[sliceSize];
        }
        return new BufferSlice<>(buffer, arena, CONFIGURED_SLICES_PER_THREAD.get());
    }

    public static BufferSlice<ByteBuffer> directSlice() {
        int sliceSize = CONFIGURED_SLICE_BYTES.get();
        Deque<ByteBuffer> arena = DIRECT_SLICES.get();
        ByteBuffer buffer = arena.pollFirst();
        if (buffer == null || buffer.capacity() < sliceSize) {
            buffer = ByteBuffer.allocateDirect(sliceSize);
        } else {
            buffer.clear();
        }
        return new BufferSlice<>(buffer, arena, CONFIGURED_SLICES_PER_THREAD.get());
    }

    public static PooledByteArrayOutputStream byteArrayOutputStream() {
        BufferSlice<byte[]> slice = heapSlice();
        return new PooledByteArrayOutputStream(slice);
    }

    public static final class BufferSlice<T> implements AutoCloseable {
        private final T buffer;
        private final Deque<T> arena;
        private final int maxRetained;
        private boolean released;

        private BufferSlice(T buffer, Deque<T> arena, int maxRetained) {
            this.buffer = buffer;
            this.arena = arena;
            this.maxRetained = maxRetained;
        }

        public T buffer() {
            return buffer;
        }

        @Override
        public void close() {
            if (released) {
                return;
            }
            released = true;
            if (arena.size() < maxRetained) {
                arena.offerFirst(buffer);
            }
        }
    }

    public static final class PooledByteArrayOutputStream extends ByteArrayOutputStream {
        private final BufferSlice<byte[]> slice;

        private PooledByteArrayOutputStream(BufferSlice<byte[]> slice) {
            super(0);
            this.slice = slice;
            this.buf = slice.buffer();
        }

        public byte[] directBuffer() {
            return buf;
        }

        public int currentSize() {
            return count;
        }

        @Override
        public void close() {
            super.reset();
            slice.close();
        }
    }
}
