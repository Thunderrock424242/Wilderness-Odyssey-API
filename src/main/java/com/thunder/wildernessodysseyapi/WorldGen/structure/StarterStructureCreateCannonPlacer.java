package com.thunder.wildernessodysseyapi.WorldGen.structure;

import com.simibubi.create.content.schematics.SchematicItem;
import com.simibubi.create.foundation.utility.CreatePaths;
import com.thunder.wildernessodysseyapi.Core.ModConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.neoforged.fml.ModList;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Optional;

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

        String fileName = schematic.fileName();
        if (fileName.isEmpty()) {
            return false;
        }

        Path cannonDir = CreatePaths.UPLOADED_SCHEMATICS_DIR.resolve(OWNER_NAMESPACE);
        try {
            Files.createDirectories(cannonDir);
            Path cannonPath = copyBundledSchematic(serverLevel, fileName, cannonDir)
                    .orElseGet(() -> copyProvidedSchematic(schematic.path(), cannonDir, fileName));
            if (cannonPath == null) {
                ModConstants.LOGGER.warn("[Starter Structure compat] No bundled or external schematic found for {}.", fileName);
                return false;
            }

            ItemStack blueprint = SchematicItem.create(serverLevel, fileName, OWNER_NAMESPACE);
            CustomData customData = blueprint.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY);
            CompoundTag tag = customData.copyTag();
            if (tag == null) {
                tag = new CompoundTag();
            }
            tag.putBoolean("Deployed", true);
            tag.put("Anchor", NbtUtils.writeBlockPos(origin));
            tag.putString("Rotation", Rotation.NONE.name());
            tag.putString("Mirror", Mirror.NONE.name());
            blueprint.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));

            StructureTemplate template = SchematicItem.loadSchematic(serverLevel, blueprint);
            StructurePlaceSettings settings = SchematicItem.getSettings(blueprint, true);
            Vec3i size = template.getSize();
            if (size.getX() == 0 || size.getY() == 0 || size.getZ() == 0) {
                ModConstants.LOGGER.debug("[Starter Structure compat] Skipping Create schematic cannon placement for {} because the template is empty.", fileName);
                return false;
            }
            template.placeInWorld(serverLevel, origin, origin, settings, serverLevel.random, Block.UPDATE_CLIENTS);

            ModConstants.LOGGER.info("[Starter Structure compat] Pasted starter bunker with Create schematic cannon at {}.", origin);
            return true;
        } catch (Exception e) {
            ModConstants.LOGGER.warn("[Starter Structure compat] Create schematic cannon placement failed for {}.", fileName, e);
            return false;
        }
    }

    /** Ensures the redirected Create schematic directory exists early in startup. */
    public static void prepareUploadDirectory() {
        if (!ModList.get().isLoaded("create")) {
            return;
        }
        try {
            Files.createDirectories(CreatePaths.UPLOADED_SCHEMATICS_DIR);
        } catch (IOException e) {
            ModConstants.LOGGER.warn("[Starter Structure compat] Failed to prepare schematic directory at {}.", CreatePaths.UPLOADED_SCHEMATICS_DIR, e);
        }
    }

    private static Optional<Path> copyBundledSchematic(ServerLevel serverLevel, String fileName, Path cannonDir) {
        ResourceManager resourceManager = serverLevel.getServer().getResourceManager();
        ResourceLocation location = ResourceLocation.fromNamespaceAndPath(ModConstants.MOD_ID, "schematics/" + fileName);
        Optional<Resource> resource = resourceManager.getResource(location);
        if (resource.isEmpty()) {
            return Optional.empty();
        }

        Path target = cannonDir.resolve(fileName);
        try (InputStream stream = resource.get().open()) {
            Files.copy(stream, target, StandardCopyOption.REPLACE_EXISTING);
            return Optional.of(target);
        } catch (IOException e) {
            ModConstants.LOGGER.warn("[Starter Structure compat] Failed to copy bundled schematic {}.", location, e);
            return Optional.empty();
        }
    }

    private static Path copyProvidedSchematic(Path schematicPath, Path cannonDir, String fileName) {
        if (schematicPath == null || !Files.isRegularFile(schematicPath)) {
            return null;
        }

        Path target = cannonDir.resolve(fileName);
        try {
            Files.copy(schematicPath, target, StandardCopyOption.REPLACE_EXISTING);
            return target;
        } catch (IOException e) {
            ModConstants.LOGGER.warn("[Starter Structure compat] Failed to copy external schematic {}.", schematicPath, e);
            return null;
        }
    }
}
