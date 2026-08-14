package com.thunder.wildernessodysseyapi.developmentstudio.region;

import com.thunder.wildernessodysseyapi.watersystem.water.api.WaterServices;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.HashSet;
import java.util.Set;

/**
 * Durable baseline for a small, explicitly registered block-reset region.
 *
 * <p>Capture rejects unloaded chunks and every vanilla or Wilderness water cell.
 * This prevents the generic block restore path from becoming a second water
 * authority or an accidental chunk-reset mechanism.</p>
 */
public final class StudioRegionBlockSnapshot {
    private static final int MAX_ENTRIES = (int) StudioTestRegionRegistry.MAX_REGION_VOLUME;

    private final ResourceLocation regionId;
    private final ResourceLocation dimension;
    private final BlockPos min;
    private final BlockPos max;
    private final List<Entry> entries;

    public StudioRegionBlockSnapshot(ResourceLocation regionId,
                                     ResourceLocation dimension,
                                     BlockPos min,
                                     BlockPos max,
                                     List<Entry> entries) {
        this.regionId = regionId;
        this.dimension = dimension;
        this.min = min.immutable();
        this.max = max.immutable();
        this.entries = List.copyOf(entries);
        long width = (long) max.getX() - min.getX() + 1L;
        long height = (long) max.getY() - min.getY() + 1L;
        long depth = (long) max.getZ() - min.getZ() + 1L;
        long volume = width * height * depth;
        if (width <= 0L || height <= 0L || depth <= 0L || volume > MAX_ENTRIES
                || this.entries.size() > volume) {
            throw new IllegalArgumentException("Studio region snapshot is too large");
        }
        Set<Long> positions = new HashSet<>();
        for (Entry entry : this.entries) {
            BlockPos relative = entry.relativePosition();
            if (relative.getX() < 0 || relative.getX() >= width
                    || relative.getY() < 0 || relative.getY() >= height
                    || relative.getZ() < 0 || relative.getZ() >= depth
                    || !positions.add(relative.asLong())) {
                throw new IllegalArgumentException("Studio region snapshot contains invalid positions");
            }
        }
    }

    /** Captures the exact registered volume before the first Studio mutation. */
    public static StudioRegionBlockSnapshot capture(ServerLevel level, StudioTestRegion region) {
        if (region.resetPolicy() != StudioResetPolicy.BLOCK_SNAPSHOT
                || !level.dimension().location().equals(region.dimension())) {
            throw new IllegalArgumentException("Region does not permit block snapshots");
        }
        if (region.volume() > MAX_ENTRIES) {
            throw new IllegalArgumentException("Region exceeds the Studio snapshot volume limit");
        }

        List<Entry> entries = new ArrayList<>((int) region.volume());
        for (BlockPos cursor : BlockPos.betweenClosed(region.min(), region.max())) {
            BlockPos position = cursor.immutable();
            if (!level.hasChunkAt(position)) {
                throw new IllegalStateException("Studio region contains an unloaded chunk");
            }
            if (!level.getFluidState(position).isEmpty() || WaterServices.access().isWaterAt(level, position)) {
                throw new IllegalStateException("Studio block snapshots cannot capture water cells");
            }

            BlockState state = level.getBlockState(position);
            BlockEntity blockEntity = level.getBlockEntity(position);
            entries.add(new Entry(
                    position.subtract(region.min()),
                    NbtUtils.writeBlockState(state),
                    blockEntity == null ? null : blockEntity.saveWithFullMetadata(level.registryAccess())
            ));
        }
        return new StudioRegionBlockSnapshot(
                region.id(), region.dimension(), region.min(), region.max(), entries
        );
    }

    /** Restores only when the current persisted region exactly matches this baseline. */
    public boolean restore(ServerLevel level, StudioTestRegion region) {
        if (!matches(region) || !level.dimension().location().equals(dimension)) {
            return false;
        }
        for (Entry entry : entries) {
            BlockPos position = min.offset(entry.relativePosition());
            if (!level.hasChunkAt(position)) {
                return false;
            }
        }

        var blocks = level.registryAccess().lookupOrThrow(Registries.BLOCK);
        for (Entry entry : entries) {
            BlockPos position = min.offset(entry.relativePosition());
            if (level.getBlockEntity(position) != null) {
                level.removeBlockEntity(position);
            }
            BlockState state = NbtUtils.readBlockState(blocks, entry.blockState());
            level.setBlock(position, state, Block.UPDATE_CLIENTS | Block.UPDATE_SUPPRESS_DROPS);
        }
        for (Entry entry : entries) {
            if (entry.blockEntity() == null) {
                continue;
            }
            BlockPos position = min.offset(entry.relativePosition());
            BlockState state = level.getBlockState(position);
            BlockEntity restored = BlockEntity.loadStatic(position, state, entry.blockEntity(), level.registryAccess());
            if (restored != null) {
                level.setBlockEntity(restored);
            }
        }
        return true;
    }

    public ResourceLocation regionId() {
        return regionId;
    }

    public int entryCount() {
        return entries.size();
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putString("region_id", regionId.toString());
        tag.putString("dimension", dimension.toString());
        tag.putLong("min", min.asLong());
        tag.putLong("max", max.asLong());
        ListTag entriesTag = new ListTag();
        for (Entry entry : entries) {
            CompoundTag entryTag = new CompoundTag();
            entryTag.putLong("relative_position", entry.relativePosition().asLong());
            entryTag.put("block_state", entry.blockState().copy());
            if (entry.blockEntity() != null) {
                entryTag.put("block_entity", entry.blockEntity().copy());
            }
            entriesTag.add(entryTag);
        }
        tag.put("entries", entriesTag);
        return tag;
    }

    /** Reads a bounded baseline; malformed snapshots are ignored by world-data loading. */
    public static Optional<StudioRegionBlockSnapshot> load(CompoundTag tag) {
        ResourceLocation regionId = ResourceLocation.tryParse(tag.getString("region_id"));
        ResourceLocation dimension = ResourceLocation.tryParse(tag.getString("dimension"));
        if (regionId == null || dimension == null) {
            return Optional.empty();
        }
        ListTag entriesTag = tag.getList("entries", Tag.TAG_COMPOUND);
        if (entriesTag.size() > MAX_ENTRIES) {
            return Optional.empty();
        }
        List<Entry> entries = new ArrayList<>(entriesTag.size());
        for (int index = 0; index < entriesTag.size(); index++) {
            CompoundTag entryTag = entriesTag.getCompound(index);
            if (!entryTag.contains("block_state", Tag.TAG_COMPOUND)) {
                return Optional.empty();
            }
            entries.add(new Entry(
                    BlockPos.of(entryTag.getLong("relative_position")),
                    entryTag.getCompound("block_state"),
                    entryTag.contains("block_entity", Tag.TAG_COMPOUND)
                            ? entryTag.getCompound("block_entity")
                            : null
            ));
        }
        try {
            return Optional.of(new StudioRegionBlockSnapshot(
                    regionId,
                    dimension,
                    BlockPos.of(tag.getLong("min")),
                    BlockPos.of(tag.getLong("max")),
                    entries
            ));
        } catch (IllegalArgumentException exception) {
            return Optional.empty();
        }
    }

    private boolean matches(StudioTestRegion region) {
        return region.id().equals(regionId)
                && region.dimension().equals(dimension)
                && region.min().equals(min)
                && region.max().equals(max)
                && region.resetPolicy() == StudioResetPolicy.BLOCK_SNAPSHOT
                && entries.size() == region.volume();
    }

    /** One relative block state plus optional block-entity data. */
    public record Entry(BlockPos relativePosition, CompoundTag blockState, CompoundTag blockEntity) {
        public Entry {
            relativePosition = relativePosition.immutable();
            blockState = blockState.copy();
            blockEntity = blockEntity == null ? null : blockEntity.copy();
        }
    }
}
