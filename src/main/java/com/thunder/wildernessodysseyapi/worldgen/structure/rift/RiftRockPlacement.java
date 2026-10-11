package com.thunder.wildernessodysseyapi.worldgen.structure.rift;

import com.thunder.wildernessodysseyapi.core.ModConstants;
import com.thunder.wildernessodysseyapi.temporalrift.blockentity.RiftCoreBlockEntity;
import com.thunder.wildernessodysseyapi.temporalrift.hybrid.RiftCompositionPlan;
import com.thunder.wildernessodysseyapi.temporalrift.hybrid.RiftConfig;
import com.thunder.wildernessodysseyapi.worldgen.processor.BlockEntityNbtSanitizingProcessor;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.templatesystem.BlockIgnoreProcessor;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Cold-path composition placement. Uses vanilla template collision, never modifies existing occupied terrain. */
public final class RiftRockPlacement {
    public static final List<String> VARIANTS = List.of("rift_rock_small_01", "rift_rock_small_02",
            "rift_rock_medium_01", "rift_rock_medium_02", "rift_platform_01", "rift_platform_02",
            "rift_anomaly_island_small_01", "rift_anomaly_island_small_02");

    public record Result(boolean placed, int pieces, String reason) { }
    private record Prepared(StructureTemplate template, BlockPos origin, StructurePlaceSettings settings) { }
    private RiftRockPlacement() { }

    /** All fit checks finish before the first template writes a block. Missing assets fail explicitly. */
    public static Result place(ServerLevel level, RiftCoreBlockEntity core) {
        if (!core.isHybrid()) return new Result(false, 0, "This core uses the legacy temporal mode.");
        if (core.isCompositionPlaced()) return new Result(false, 0, "This core already has a composition; player edits are preserved.");
        Map<String, StructureTemplate> templates = new LinkedHashMap<>();
        List<RiftCompositionPlan.Variant> dimensions = new ArrayList<>();
        for (String name : VARIANTS) {
            ResourceLocation id = ResourceLocation.fromNamespaceAndPath(ModConstants.MOD_ID, name);
            var found = level.getStructureManager().get(id);
            if (found.isEmpty()) return new Result(false, 0, "Missing template: " + id);
            var size = found.get().getSize();
            if (size.getX() < 1 || size.getY() < 1 || size.getZ() < 1
                    || size.getX() > 9 || size.getY() > 8 || size.getZ() > 9) {
                return new Result(false, 0, "Template exceeds the 9x8x9 rock budget: " + id);
            }
            templates.put(name, found.get());
            if (!hasPlaceableBlocks(found.get())) return new Result(false, 0, "Empty/air-only rock template: " + id);
            dimensions.add(new RiftCompositionPlan.Variant(name, size.getX(), size.getY(), size.getZ()));
        }
        BlockPos anchor = core.getBlockPos();
        long seed = level.getSeed() ^ anchor.asLong() ^ 0x5A16E4B3L;
        var plan = RiftCompositionPlan.plan(seed, RiftConfig.FORMATION_DIAMETER.get(),
                RiftConfig.ROCK_COUNT.get(), RiftConfig.ROCK_SPACING.get(), dimensions);
        if (plan.isEmpty()) return new Result(false, 0, "No non-intersecting rocks fit the configured composition.");
        List<Prepared> prepared = new ArrayList<>();
        for (var piece : plan) {
            var volume = piece.box();
            BoundingBox box = new BoundingBox(anchor.getX() + volume.minX(), anchor.getY() + volume.minY(), anchor.getZ() + volume.minZ(),
                    anchor.getX() + volume.maxX() - 1, anchor.getY() + volume.maxY() - 1, anchor.getZ() + volume.maxZ() - 1);
            if (box.minY() < level.getMinBuildHeight() || box.maxY() >= level.getMaxBuildHeight()) {
                return new Result(false, 0, "A rock would exceed the world build height.");
            }
            // Inspect full footprints without asking Minecraft to load their chunks.
            for (int x = box.minX() >> 4; x <= box.maxX() >> 4; x++) {
                for (int z = box.minZ() >> 4; z <= box.maxZ() >> 4; z++) {
                    if (level.getChunkSource().getChunkNow(x, z) == null) return new Result(false, 0, "A rock footprint crosses an unloaded chunk.");
                }
            }
            for (BlockPos pos : BlockPos.betweenClosed(box.minX(), box.minY(), box.minZ(), box.maxX(), box.maxY(), box.maxZ())) {
                if (!level.getWorldBorder().isWithinBounds(pos)) return new Result(false, 0, "A rock footprint crosses the world border.");
                if (!level.getBlockState(pos).isAir() || level.getBlockEntity(pos) != null) {
                    return new Result(false, 0, "Occupied rock footprint at " + pos.toShortString() + "; try a higher, clear site.");
                }
            }
            BlockPos origin = anchor.offset(piece.x(), piece.y(), piece.z());
            StructurePlaceSettings settings = new StructurePlaceSettings().setRotation(Rotation.values()[piece.rotation()])
                    .setIgnoreEntities(true).setKnownShape(true).setBoundingBox(box)
                    .addProcessor(BlockIgnoreProcessor.STRUCTURE_AND_AIR)
                    .addProcessor(new BlockEntityNbtSanitizingProcessor(ResourceLocation.fromNamespaceAndPath(ModConstants.MOD_ID, piece.variant().id())));
            StructureTemplate template = templates.get(piece.variant().id());
            prepared.add(new Prepared(template, origin, settings));
        }
        // Keep snapshots until commit finishes. An unexpected template refusal rolls back only this synchronous operation.
        Map<BlockPos, net.minecraft.world.level.block.state.BlockState> before = new LinkedHashMap<>();
        for (Prepared piece : prepared) {
            BoundingBox box = piece.settings.getBoundingBox();
            for (BlockPos pos : BlockPos.betweenClosed(box.minX(), box.minY(), box.minZ(), box.maxX(), box.maxY(), box.maxZ())) {
                before.put(pos.immutable(), level.getBlockState(pos));
            }
        }
        try {
            RandomSource random = RandomSource.create(seed);
            for (Prepared piece : prepared) {
                if (!piece.template.placeInWorld(level, piece.origin, piece.origin, piece.settings, random, Block.UPDATE_CLIENTS)) {
                    throw new IllegalStateException("Template refused placement");
                }
            }
        } catch (RuntimeException exception) {
            before.forEach((pos, state) -> level.setBlock(pos, state, Block.UPDATE_CLIENTS));
            ModConstants.LOGGER.warn("Hybrid rift composition rolled back at {}", anchor, exception);
            return new Result(false, 0, "Template placement failed; the preflight volume was restored.");
        }
        core.markCompositionPlaced();
        return new Result(true, prepared.size(), "Placed " + prepared.size() + " rocks.");
    }

    private static boolean hasPlaceableBlocks(StructureTemplate template) {
        // Vanilla serialization exposes palettes without reflection or assuming the artist chose a particular material.
        CompoundTag stored = template.save(new CompoundTag());
        ListTag blocks = stored.getList("blocks", Tag.TAG_COMPOUND);
        List<ListTag> palettes = new ArrayList<>();
        if (stored.contains("palette")) palettes.add(stored.getList("palette", Tag.TAG_COMPOUND));
        else for (Tag palette : stored.getList("palettes", Tag.TAG_LIST)) palettes.add((ListTag) palette);
        if (palettes.isEmpty()) return false;
        for (ListTag palette : palettes) {
            boolean found = false;
            for (Tag block : blocks) {
                int index = ((CompoundTag) block).getInt("state");
                if (index < 0 || index >= palette.size()) continue;
                ResourceLocation name = ResourceLocation.tryParse(palette.getCompound(index).getString("Name"));
                if (name == null) continue;
                var state = BuiltInRegistries.BLOCK.get(name).defaultBlockState();
                if (!state.isAir() && !state.is(Blocks.STRUCTURE_BLOCK)) { found = true; break; }
            }
            if (!found) return false;
        }
        return true;
    }
}
