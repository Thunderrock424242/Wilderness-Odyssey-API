package com.thunder.wildernessodysseyapi.watersystem.water.render;

import net.minecraft.core.SectionPos;
import net.minecraft.world.level.ChunkPos;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Generation-aware ownership state for baked and custom water surfaces.
 *
 * <p>The old baked section remains the sole visible owner while suppression is
 * compiling. Custom geometry becomes visible only after every expected section
 * has uploaded a build that compiled under the current owned-top mask.</p>
 */
final class WaterSurfaceHandoff {

    enum Phase {
        FALLBACK_VISIBLE,
        SUPPRESSION_PENDING,
        CUSTOM_VISIBLE
    }

    private final AtomicLong nextGeneration = new AtomicLong();
    private final Map<Long, ChunkState> chunks = new ConcurrentHashMap<>();
    private final ThreadLocal<CompilationObservation> compilation = new ThreadLocal<>();

    long beginSuppression(long chunkKey, Map<Long, SectionMask> sections) {
        Map<Long, SectionMask> immutableSections = Map.copyOf(sections);
        ChunkState next = chunks.compute(chunkKey, (key, current) -> {
            // Sea-state/snapshot refreshes can rebuild identical ownership while
            // the renderer is uploading. Keep those receipts and partial upload
            // progress valid: the uploaded build has already omitted these tops.
            if (current != null && current.sections().equals(immutableSections)) {
                return current;
            }
            return new ChunkState(
                    nextGeneration.incrementAndGet(),
                    immutableSections.isEmpty() ? Phase.CUSTOM_VISIBLE : Phase.SUPPRESSION_PENDING,
                    immutableSections,
                    Set.copyOf(immutableSections.keySet())
            );
        });
        return next.generation();
    }

    void keepCustomVisible(long chunkKey, Map<Long, SectionMask> sections) {
        long generation = nextGeneration.incrementAndGet();
        chunks.put(chunkKey, new ChunkState(
                generation,
                Phase.CUSTOM_VISIBLE,
                Map.copyOf(sections),
                Set.of()
        ));
    }

    Phase phase(long chunkKey) {
        ChunkState state = chunks.get(chunkKey);
        return state == null ? Phase.FALLBACK_VISIBLE : state.phase();
    }

    boolean suppressionRequested(long chunkKey) {
        return phase(chunkKey) != Phase.FALLBACK_VISIBLE;
    }

    boolean customVisible(long chunkKey) {
        return phase(chunkKey) == Phase.CUSTOM_VISIBLE;
    }

    Set<Long> trackedSections(long chunkKey) {
        ChunkState state = chunks.get(chunkKey);
        return state == null ? Set.of() : state.sections().keySet();
    }

    void beginCompilation(long sectionKey) {
        compilation.remove();
        long chunkKey = chunkKey(sectionKey);
        ChunkState state = chunks.get(chunkKey);
        if (state == null || state.phase() != Phase.SUPPRESSION_PENDING) {
            return;
        }
        SectionMask expected = state.sections().get(sectionKey);
        if (expected != null) {
            compilation.set(new CompilationObservation(
                    chunkKey, sectionKey, state.generation(), expected));
        }
    }

    /**
     * Returns whether the current compilation may suppress one fallback top.
     *
     * <p>A build that started before the current generation deliberately keeps
     * the fallback. Otherwise that stale build could upload a hole without ever
     * carrying the receipt needed to publish the replacement mesh.</p>
     */
    boolean shouldSuppressTop(long sectionKey, int columnIndex) {
        long chunkKey = chunkKey(sectionKey);
        ChunkState state = chunks.get(chunkKey);
        if (state == null || !state.sections().getOrDefault(sectionKey, SectionMask.EMPTY)
                .contains(columnIndex)) {
            return false;
        }
        if (state.phase() == Phase.CUSTOM_VISIBLE) {
            return true;
        }
        if (state.phase() != Phase.SUPPRESSION_PENDING) {
            return false;
        }
        CompilationObservation observation = compilation.get();
        if (observation == null
                || observation.sectionKey != sectionKey
                || observation.chunkKey != chunkKey
                || observation.generation != state.generation()
                || !observation.expected.contains(columnIndex)) {
            return false;
        }
        return true;
    }

    WaterHandoffReceipt finishCompilation(long sectionKey) {
        CompilationObservation observation = compilation.get();
        compilation.remove();
        if (observation == null || observation.sectionKey != sectionKey) {
            return WaterHandoffReceipt.NONE;
        }
        // A successful section build need not call the fluid hook for every
        // expected column (occluded/hosted water may be skipped by the renderer).
        // Rejecting it after it suppressed even one top leaves a permanent hole.
        // The build/upload lifecycle, not callback coverage, is the receipt.
        ChunkState state = chunks.get(observation.chunkKey);
        if (state == null
                || state.phase() != Phase.SUPPRESSION_PENDING
                || state.generation() != observation.generation
                || !observation.expected.equals(state.sections().get(sectionKey))) {
            return WaterHandoffReceipt.NONE;
        }
        return new WaterHandoffReceipt(
                observation.chunkKey, sectionKey, observation.generation);
    }

    boolean acknowledgeUpload(WaterHandoffReceipt receipt) {
        if (receipt == null || !receipt.valid()) {
            return false;
        }
        AtomicBoolean becameVisible = new AtomicBoolean(false);
        chunks.computeIfPresent(receipt.chunkKey(), (chunkKey, state) -> {
            if (state.phase() != Phase.SUPPRESSION_PENDING
                    || state.generation() != receipt.generation()
                    || !state.remainingSections().contains(receipt.sectionKey())) {
                return state;
            }
            Set<Long> remaining = new HashSet<>(state.remainingSections());
            remaining.remove(receipt.sectionKey());
            if (remaining.isEmpty()) {
                becameVisible.set(true);
                return new ChunkState(
                        state.generation(),
                        Phase.CUSTOM_VISIBLE,
                        state.sections(),
                        Set.of()
                );
            }
            return new ChunkState(
                    state.generation(),
                    state.phase(),
                    state.sections(),
                    Set.copyOf(remaining)
            );
        });
        return becameVisible.get();
    }

    void remove(long chunkKey) {
        chunks.remove(chunkKey);
    }

    void clear() {
        chunks.clear();
        compilation.remove();
    }

    private static long chunkKey(long sectionKey) {
        return ChunkPos.asLong(SectionPos.x(sectionKey), SectionPos.z(sectionKey));
    }

    record SectionMask(long mask0, long mask1, long mask2, long mask3) {

        static final SectionMask EMPTY = new SectionMask(0L, 0L, 0L, 0L);

        boolean contains(int columnIndex) {
            long mask = switch (columnIndex >>> 6) {
                case 0 -> mask0;
                case 1 -> mask1;
                case 2 -> mask2;
                default -> mask3;
            };
            return (mask & (1L << (columnIndex & 63))) != 0L;
        }

    }

    private record ChunkState(
            long generation,
            Phase phase,
            Map<Long, SectionMask> sections,
            Set<Long> remainingSections
    ) {
    }

    private static final class CompilationObservation {
        private final long chunkKey;
        private final long sectionKey;
        private final long generation;
        private final SectionMask expected;

        private CompilationObservation(
                long chunkKey,
                long sectionKey,
                long generation,
                SectionMask expected
        ) {
            this.chunkKey = chunkKey;
            this.sectionKey = sectionKey;
            this.generation = generation;
            this.expected = expected;
        }

    }
}
