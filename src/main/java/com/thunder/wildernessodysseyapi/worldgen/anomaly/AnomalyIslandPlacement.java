package com.thunder.wildernessodysseyapi.worldgen.anomaly;

import com.thunder.wildernessodysseyapi.core.ModConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;

import java.util.List;

/**
 * Places small stationary islands through Minecraft's template API after a complete read-only preflight.
 * Only one already-available chunk is inspected or written. No terrain clearing, entities or fluid sources
 * are permitted, and no template cache retains a world beyond Minecraft's own template-manager lifecycle.
 */
public final class AnomalyIslandPlacement {
    private static final List<ResourceLocation> TEMPLATES = List.of(id("anomaly_island_small_01"),
            id("anomaly_island_small_02"), id("anomaly_island_small_03"));

    /** A rejection is suitable for operator diagnostics without revealing unrelated world data. */
    public record Inspection(boolean allowed, AnomalyIslandSafety.Rejection reason,
                             String detail, BoundingBox bounds) { }

    private AnomalyIslandPlacement() { }

    public static List<ResourceLocation> templates() { return TEMPLATES; }

    /** Inspects a known authored template without writing a block or requesting a missing chunk. */
    public static Inspection inspect(ServerLevelAccessor level, ResourceLocation templateId, BlockPos origin) {
        return preflight(level, templateId, origin).inspection;
    }

    /** Repeats preflight immediately before writing; callers must execute on the owning world thread. */
    public static Inspection place(ServerLevelAccessor level, ResourceLocation templateId, BlockPos origin) {
        Prepared prepared = preflight(level, templateId, origin);
        if (!prepared.inspection.allowed) {
            return prepared.inspection;
        }
        StructurePlaceSettings settings = new StructurePlaceSettings()
                .setBoundingBox(prepared.inspection.bounds)
                .setIgnoreEntities(true)
                .setKnownShape(true);
        RandomSource random = RandomSource.create(level.getLevel().getSeed() ^ origin.asLong());
        boolean placed = prepared.template.placeInWorld(level, origin, origin, settings, random,
                Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
        return placed ? prepared.inspection : rejected(AnomalyIslandSafety.Rejection.PLACE_FAILED,
                prepared.inspection.bounds);
    }

    private static Prepared preflight(ServerLevelAccessor level, ResourceLocation templateId, BlockPos origin) {
        if (!TEMPLATES.contains(templateId)) {
            return failed(AnomalyIslandSafety.Rejection.TEMPLATE_UNKNOWN, null);
        }
        var optional = level.getLevel().getStructureManager().get(templateId);
        if (optional.isEmpty()) {
            return failed(AnomalyIslandSafety.Rejection.TEMPLATE_MISSING, null);
        }
        StructureTemplate template = optional.get();
        Vec3i size = template.getSize();
        if (size.getX() <= 0 || size.getX() > AnomalyIslandSafety.MAX_WIDTH
                || size.getZ() <= 0 || size.getZ() > AnomalyIslandSafety.MAX_WIDTH
                || size.getY() <= 0 || size.getY() > AnomalyIslandSafety.MAX_HEIGHT) {
            return failed(AnomalyIslandSafety.Rejection.INVALID_BOUNDS, null);
        }
        long maxX = (long) origin.getX() + size.getX() - 1;
        long maxY = (long) origin.getY() + size.getY() - 1;
        long maxZ = (long) origin.getZ() + size.getZ() - 1;
        if (maxX > Integer.MAX_VALUE || maxY > Integer.MAX_VALUE || maxZ > Integer.MAX_VALUE) {
            return failed(AnomalyIslandSafety.Rejection.INVALID_BOUNDS, null);
        }
        BoundingBox bounds = new BoundingBox(origin.getX(), origin.getY(), origin.getZ(),
                (int) maxX, (int) maxY, (int) maxZ);
        if (!safeTemplateContent(template)) {
            return failed(AnomalyIslandSafety.Rejection.TEMPLATE_CONTENT, bounds);
        }
        AnomalyIslandSafety.Rejection reason = AnomalyIslandSafety.evaluate(
                new AnomalyIslandSafety.Bounds(bounds.minX(), bounds.minY(), bounds.minZ(),
                        bounds.maxX(), bounds.maxY(), bounds.maxZ()),
                level.getMinBuildHeight(), level.getMaxBuildHeight(), new LoadedSite(level));
        return reason == AnomalyIslandSafety.Rejection.NONE
                ? new Prepared(new Inspection(true, reason, "Empty elevated space is safe for the authored template.", bounds), template)
                : failed(reason, bounds);
    }

    // Reloaded datapacks may replace our template names. Recheck their bounded content rather than trusting an ID.
    private static boolean safeTemplateContent(StructureTemplate template) {
        CompoundTag tag = template.save(new CompoundTag());
        var blocks = tag.getList("blocks", Tag.TAG_COMPOUND);
        if (blocks.isEmpty() || blocks.size() > AnomalyIslandSafety.MAX_WIDTH
                * AnomalyIslandSafety.MAX_WIDTH * AnomalyIslandSafety.MAX_HEIGHT
                || !tag.getList("entities", Tag.TAG_COMPOUND).isEmpty()) {
            return false;
        }
        int paletteSize;
        if (tag.contains("palette", Tag.TAG_LIST)) {
            var palette = tag.getList("palette", Tag.TAG_COMPOUND);
            if (!safePalette(palette)) {
                return false;
            }
            paletteSize = palette.size();
        } else {
            var palettes = tag.getList("palettes", Tag.TAG_LIST);
            if (palettes.isEmpty() || palettes.size() > 16) {
                return false;
            }
            paletteSize = Integer.MAX_VALUE;
            for (int index = 0; index < palettes.size(); index++) {
                var palette = palettes.getList(index);
                if (!safePalette(palette)) {
                    return false;
                }
                paletteSize = Math.min(paletteSize, palette.size());
            }
        }
        Vec3i size = template.getSize();
        for (int index = 0; index < blocks.size(); index++) {
            CompoundTag block = blocks.getCompound(index);
            var pos = block.getList("pos", Tag.TAG_INT);
            if (block.contains("nbt") || !block.contains("state", Tag.TAG_INT) || pos.size() != 3
                    || !AnomalyIslandSafety.templateBlockFits(size.getX(), size.getY(), size.getZ(), paletteSize,
                    pos.getInt(0), pos.getInt(1), pos.getInt(2), block.getInt("state"))) {
                return false;
            }
        }
        return true;
    }

    private static boolean safePalette(net.minecraft.nbt.ListTag palette) {
        if (palette.isEmpty() || palette.size() > AnomalyIslandSafety.MAX_WIDTH
                * AnomalyIslandSafety.MAX_WIDTH * AnomalyIslandSafety.MAX_HEIGHT) {
            return false;
        }
        for (int index = 0; index < palette.size(); index++) {
            CompoundTag entry = palette.getCompound(index);
            ResourceLocation blockId = ResourceLocation.tryParse(entry.getString("Name"));
            if (blockId == null || !"minecraft".equals(blockId.getNamespace())
                    || !BuiltInRegistries.BLOCK.containsKey(blockId)) {
                return false;
            }
            var block = BuiltInRegistries.BLOCK.get(blockId);
            if (block.defaultBlockState().hasBlockEntity() || !block.defaultBlockState().getFluidState().isEmpty()
                    || block instanceof net.minecraft.world.level.block.FallingBlock
                    || "true".equals(entry.getCompound("Properties").getString("waterlogged"))) {
                return false;
            }
        }
        return true;
    }

    /** Uses the worldgen region's existing cache or the live server's loaded chunk map, never a loading lookup. */
    static ChunkAccess availableChunk(ServerLevelAccessor level, int chunkX, int chunkZ) {
        if (level instanceof ServerLevel serverLevel) {
            return serverLevel.getChunkSource().getChunkNow(chunkX, chunkZ);
        }
        if (level instanceof WorldGenLevel worldgen && worldgen.hasChunk(chunkX, chunkZ)) {
            return worldgen.getChunk(chunkX, chunkZ);
        }
        return null;
    }

    static boolean structureProtected(ChunkAccess chunk) {
        return !chunk.getAllStarts().isEmpty()
                || chunk.getAllReferences().values().stream().anyMatch(references -> !references.isEmpty());
    }

    static boolean canWrite(ServerLevelAccessor level, BlockPos pos) {
        return pos.getY() >= level.getMinBuildHeight() && pos.getY() < level.getMaxBuildHeight()
                && level.getLevel().getWorldBorder().isWithinBounds(pos)
                && (!(level instanceof WorldGenLevel worldgen) || worldgen.ensureCanWrite(pos));
    }

    private static Prepared failed(AnomalyIslandSafety.Rejection reason, BoundingBox bounds) {
        return new Prepared(rejected(reason, bounds), null);
    }

    private static Inspection rejected(AnomalyIslandSafety.Rejection reason, BoundingBox bounds) {
        return new Inspection(false, reason, switch (reason) {
            case TEMPLATE_UNKNOWN -> "Choose one of the three authored small islands.";
            case TEMPLATE_MISSING -> "The structure template is unavailable; generate/package StructureGen resources.";
            case TEMPLATE_CONTENT -> "The template has unsafe content, fluids, entities or a block entity.";
            case INVALID_BOUNDS -> "The template exceeds the small-island bounds.";
            case CHUNK_BOUNDARY -> "The template and one-block air margin must fit inside one chunk.";
            case BUILD_HEIGHT -> "The template needs room inside the build-height margins.";
            case CHUNK_UNAVAILABLE -> "The target chunk is not already available.";
            case STRUCTURE_PRESENT -> "The target chunk has a structure start or reference.";
            case GROUND_CLEARANCE -> "The island needs at least eight blocks of separation above the surface.";
            case BLOCK_ENTITY -> "A block entity occupies the target clearance volume.";
            case OBSTRUCTED -> "The complete template bounds and air margin must be empty.";
            case WRITE_UNAVAILABLE -> "The target is outside the world border or writable worldgen region.";
            case PLACE_FAILED -> "Minecraft rejected the preflighted template placement.";
            case NONE -> "Placement accepted.";
        }, bounds);
    }

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(ModConstants.MOD_ID, path);
    }

    private record Prepared(Inspection inspection, StructureTemplate template) { }

    private static final class LoadedSite implements AnomalyIslandSafety.Site {
        private final ServerLevelAccessor level;
        private ChunkAccess chunk;

        private LoadedSite(ServerLevelAccessor level) { this.level = level; }
        public boolean chunkAvailable(int x, int z) {
            chunk = availableChunk(level, x, z);
            return chunk != null;
        }
        public boolean structureProtected(int x, int z) { return AnomalyIslandPlacement.structureProtected(chunk); }
        public int groundHeight(int x, int z) {
            Heightmap.Types type = level instanceof ServerLevel ? Heightmap.Types.WORLD_SURFACE : Heightmap.Types.WORLD_SURFACE_WG;
            return chunk.getHeight(type, x & 15, z & 15);
        }
        public boolean blockEntityAt(int x, int y, int z) {
            BlockPos pos = new BlockPos(x, y, z);
            return chunk.getBlockState(pos).hasBlockEntity() || chunk.getBlockEntity(pos) != null;
        }
        public boolean airAt(int x, int y, int z) { return chunk.getBlockState(new BlockPos(x, y, z)).isAir(); }
        public boolean canWriteAt(int x, int y, int z) { return canWrite(level, new BlockPos(x, y, z)); }
    }
}
