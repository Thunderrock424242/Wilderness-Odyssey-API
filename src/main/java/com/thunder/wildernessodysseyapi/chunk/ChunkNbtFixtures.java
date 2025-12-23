package com.thunder.wildernessodysseyapi.chunk;

import net.minecraft.nbt.ByteArrayTag;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.LongArrayTag;
import net.minecraft.world.level.ChunkPos;

import java.util.Random;

/**
 * Utilities for generating repeatable synthetic chunk NBT payloads used in tests and benchmarks.
 */
public final class ChunkNbtFixtures {
    private static final int SECTION_SIDE = 16;
    private static final int SECTION_VOLUME = SECTION_SIDE * SECTION_SIDE * SECTION_SIDE;

    private ChunkNbtFixtures() {
    }

    /**
     * Builds a deterministic chunk payload with repeated sections and randomized block/state data.
     *
     * @param pos          chunk position to embed
     * @param seed         seed used for random data generation
     * @param sectionCount number of vertical chunk sections to synthesize
     * @return populated {@link CompoundTag}
     */
    public static CompoundTag sampleChunk(ChunkPos pos, int seed, int sectionCount) {
        Random random = new Random(seed ^ (pos.x * 341873128712L) ^ (pos.z * 132897987541L));

        CompoundTag chunk = new CompoundTag();
        chunk.putInt("xPos", pos.x);
        chunk.putInt("zPos", pos.z);
        chunk.putString("Status", "full");
        chunk.putLong("InhabitedTime", Math.abs(random.nextLong()));
        chunk.putInt("DataVersion", 3700);

        chunk.put("Sections", buildSections(random, sectionCount));
        chunk.put("Heightmaps", buildHeightmaps(random));
        chunk.put("Structures", buildStructuresTag(pos));
        chunk.put("CarvingMasks", new CompoundTag()); // present but empty

        return chunk;
    }

    private static ListTag buildSections(Random random, int sectionCount) {
        ListTag sections = new ListTag();
        for (int y = 0; y < sectionCount; y++) {
            CompoundTag section = new CompoundTag();
            section.putByte("Y", (byte) y);
            section.put("BlockStates", new ByteArrayTag(randomBytes(random, SECTION_VOLUME)));
            section.put("Biomes", new ByteArrayTag(randomBytes(random, SECTION_SIDE)));
            sections.add(section);
        }
        return sections;
    }

    private static CompoundTag buildHeightmaps(Random random) {
        CompoundTag heightmaps = new CompoundTag();
        long[] motionBlocking = new long[SECTION_SIDE];
        for (int i = 0; i < motionBlocking.length; i++) {
            motionBlocking[i] = Math.abs(random.nextLong() % 256);
        }
        heightmaps.put("MOTION_BLOCKING", new LongArrayTag(motionBlocking));
        return heightmaps;
    }

    private static CompoundTag buildStructuresTag(ChunkPos pos) {
        CompoundTag structures = new CompoundTag();
        structures.putLong("References", 0L);

        ListTag starts = new ListTag();
        CompoundTag structure = new CompoundTag();
        structure.putString("id", "wildernessodysseyapi:test_structure");
        structure.put("Chunk", new LongArrayTag(new long[]{pos.toLong()}));
        starts.add(structure);
        structures.put("Starts", starts);

        ListTag children = new ListTag();
        CompoundTag child = new CompoundTag();
        child.putString("id", "wildernessodysseyapi:child");
        child.put("Junctions", new ListTag());
        children.add(child);
        structures.put("Children", children);
        return structures;
    }

    private static byte[] randomBytes(Random random, int length) {
        byte[] data = new byte[length];
        random.nextBytes(data);
        return data;
    }
}
