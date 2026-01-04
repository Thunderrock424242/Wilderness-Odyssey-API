package com.thunder.wildernessodysseyapi.WorldGen.structure;

import com.simibubi.create.content.schematics.SchematicItem;
import com.simibubi.create.foundation.utility.CreatePaths;
import com.thunder.wildernessodysseyapi.Core.ModConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.neoforged.fml.ModList;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/**
 * Attempts to place starter structures using Create's schematic cannon pipeline before falling back to
 * WorldEdit or vanilla template placement.
 */
public final class StarterStructureCreateCannonPlacer {
    private static final String OWNER_NAMESPACE = "wildernessodysseyapi";

    private StarterStructureCreateCannonPlacer() {
    }

    /**
     * Attempts to paste the given schematic using Create's schematic cannon format. Returns {@code true}
     * when Create is present and the placement succeeds.
     */
    public static boolean placeWithSchematicannon(ServerLevel serverLevel, StarterStructureSchematic schematic, BlockPos origin) {
        if (!ModList.get().isLoaded("create")) return false;
        if (serverLevel == null || schematic == null || origin == null) return false;

        Path schematicPath = schematic.path();
        if (schematicPath == null || !Files.isRegularFile(schematicPath)) return false;

        String fileName = schematicPath.getFileName().toString();
        Path cannonDir = CreatePaths.UPLOADED_SCHEMATICS_DIR.resolve(OWNER_NAMESPACE);
        try {
            Files.createDirectories(cannonDir);
            Path cannonPath = cannonDir.resolve(fileName);
            Files.copy(schematicPath, cannonPath, StandardCopyOption.REPLACE_EXISTING);

            ItemStack blueprint = SchematicItem.create(serverLevel, fileName, OWNER_NAMESPACE);
            CompoundTag tag = blueprint.getOrCreateTag();
            tag.putBoolean("Deployed", true);
            tag.put("Anchor", NbtUtils.writeBlockPos(origin));
            tag.putString("Rotation", Rotation.NONE.name());
            tag.putString("Mirror", Mirror.NONE.name());
            blueprint.setTag(tag);

            StructureTemplate template = SchematicItem.loadSchematic(serverLevel, blueprint);
            StructurePlaceSettings settings = SchematicItem.getSettings(blueprint, true);
            Vec3i size = template.getSize();
            if (size.getX() == 0 || size.getY() == 0 || size.getZ() == 0) {
                ModConstants.LOGGER.debug("[Starter Structure compat] Skipping Create schematic cannon placement for {} because the template is empty.", schematicPath);
                return false;
            }
            template.placeInWorld(serverLevel, origin, origin, settings, serverLevel.random, Block.UPDATE_CLIENTS);

            ModConstants.LOGGER.info("[Starter Structure compat] Pasted starter bunker with Create schematic cannon at {}.", origin);
            return true;
        } catch (Exception e) {
            ModConstants.LOGGER.warn("[Starter Structure compat] Create schematic cannon placement failed for {}.", schematicPath, e);
            return false;
        }
    }
}
