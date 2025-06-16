package com.thunder.wildernessodysseyapi.WorldGen.debug;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureStart;

public class SafeStructureGenerator {
    public static StructureStart safeGenerate(Structure structure, Structure.GenerationContext context) {
        try {
            return structure.generate(context);
        } catch (Exception e) {
            ServerLevel level = context.chunkGenerator().getLevel();
            ChunkPos chunkPos = context.chunkPos();
            BlockPos center = chunkPos.getMiddleBlockPosition(64);
            ResourceLocation id = BuiltInRegistries.STRUCTURE.getKey(structure);
            WorldgenErrorTracker.logError("structure", id, center, level, e);
            return StructureStart.INVALID_START;
        }
    }
}