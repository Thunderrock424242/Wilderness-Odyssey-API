package com.thunder.wildernessodysseyapi.WorldGen.structure;

import com.mojang.datafixers.util.Pair;
import com.thunder.wildernessodysseyapi.Core.ModConstants;
import com.natamus.collective_common_neoforge.schematic.ParsedSchematicObject;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.phys.AABB;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Backfills entity data for WorldEdit .schem files Starter Structure fails to
 * populate when parsing schematics. Without this Create's glue/contraption
 * entities never spawn and the starter base falls apart.
 */
public final class SchematicEntityRestorer {
    private SchematicEntityRestorer() {
    }

    public static List<Pair<BlockPos, Entity>> backfillEntitiesFromSchem(ServerLevel level, Path schematicPath, boolean isNbtFormat,
                                                                         BlockPos structurePos, ParsedSchematicObject parsed) {
        if (parsed == null || isNbtFormat || schematicPath == null) {
            return List.of();
        }
        if (parsed.entities != null && !parsed.entities.isEmpty()) {
            return List.copyOf(parsed.entities);
        }

        String fileName = schematicPath.getFileName().toString().toLowerCase(Locale.ROOT);
        if (!fileName.endsWith(".schem")) {
            return List.of();
        }

        List<Pair<BlockPos, Entity>> restored = new ArrayList<>();
        try {
            CompoundTag root = NbtIo.readCompressed(schematicPath, NbtAccounter.unlimitedHeap());
            if (root == null) {
                return List.of();
            }
            if (root.contains("Schematic", Tag.TAG_COMPOUND)) {
                root = root.getCompound("Schematic");
            }

            ListTag entities = root.getList("Entities", Tag.TAG_COMPOUND);
            if (entities.isEmpty()) {
                return List.of();
            }

            for (Tag entry : entities) {
                if (!(entry instanceof CompoundTag entityTag)) {
                    continue;
                }

                BlockPos relativePos = extractRelativePos(entityTag);
                if (relativePos == null) {
                    continue;
                }

                CompoundTag entityData = entityTag.contains("nbt", Tag.TAG_COMPOUND)
                        ? entityTag.getCompound("nbt").copy()
                        : entityTag.copy();
                Entity entity = EntityType.create(entityData, level).orElse(null);
                if (entity == null) {
                    continue;
                }

                BlockPos worldPos = structurePos.offset(relativePos);
                entity.setPos(worldPos.getX() + 0.5D, worldPos.getY(), worldPos.getZ() + 0.5D);
                restored.add(Pair.of(worldPos, entity));
            }
        } catch (Exception e) {
            ModConstants.LOGGER.warn("[Starter Structure compat] Failed to backfill entities from schem {}.", schematicPath, e);
            return List.of();
        }

        if (restored.isEmpty()) {
            return List.of();
        }

        List<Pair<BlockPos, Entity>> captured = List.copyOf(restored);
        parsed.entities = new ArrayList<>(captured);
        ModConstants.LOGGER.info(
                "[Starter Structure compat] Restored {} schematic entities from {} to preserve Create contraptions.",
                captured.size(), schematicPath.getFileName());

        return captured;
    }

    public static int spawnRestoredEntities(ServerLevel level, List<Pair<BlockPos, Entity>> restored) {
        if (level == null || restored == null || restored.isEmpty()) {
            return 0;
        }

        int spawned = 0;
        for (Pair<BlockPos, Entity> entry : restored) {
            if (entry == null) {
                continue;
            }

            BlockPos pos = entry.getFirst();
            Entity entity = entry.getSecond();
            if (pos == null || entity == null) {
                continue;
            }

            if (!level.hasChunkAt(pos)) {
                continue;
            }

            AABB bounds = entity.getBoundingBox().inflate(0.25D);
            if (!level.getEntitiesOfClass(entity.getClass(), bounds).isEmpty()) {
                continue;
            }

            if (level.tryAddFreshEntityWithPassengers(entity)) {
                spawned++;
            }
        }

        if (spawned > 0) {
            ModConstants.LOGGER.info(
                    "[Starter Structure compat] Spawned {} missing schematic entities to keep Create contraptions intact.",
                    spawned);
        }

        return spawned;
    }

    private static BlockPos extractRelativePos(CompoundTag entityTag) {
        if (entityTag.contains("blockPos", Tag.TAG_LIST)) {
            ListTag blockPosList = entityTag.getList("blockPos", Tag.TAG_INT);
            if (blockPosList.size() == 3) {
                return new BlockPos(blockPosList.getInt(0), blockPosList.getInt(1), blockPosList.getInt(2));
            }
        }

        if (entityTag.contains("Pos", Tag.TAG_LIST)) {
            ListTag posList = entityTag.getList("Pos", Tag.TAG_DOUBLE);
            if (posList.size() == 3) {
                return new BlockPos(
                        Mth.floor(posList.getDouble(0)),
                        Mth.floor(posList.getDouble(1)),
                        Mth.floor(posList.getDouble(2))
                );
            }
        }

        return null;
    }
}
