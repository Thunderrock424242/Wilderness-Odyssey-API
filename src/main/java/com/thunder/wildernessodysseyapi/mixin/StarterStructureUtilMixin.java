package com.thunder.wildernessodysseyapi.mixin;

import com.natamus.starterstructure_common_neoforge.util.Util;
import com.thunder.wildernessodysseyapi.Core.ModConstants;
import com.thunder.wildernessodysseyapi.WorldGen.structure.SchematicEntityRestorer;
import com.thunder.wildernessodysseyapi.WorldGen.structure.StarterStructureSchematic;
import com.thunder.wildernessodysseyapi.WorldGen.structure.StarterStructureTerrainBlender;
import com.thunder.wildernessodysseyapi.WorldGen.structure.StarterStructureSpawnGuard;
import com.natamus.collective_common_neoforge.schematic.ParsedSchematicObject;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import com.thunder.wildernessodysseyapi.WorldGen.structure.StarterStructureWorldEditPlacer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.spongepowered.asm.mixin.injection.callback.LocalCapture;

import java.io.File;
import java.io.FileInputStream;

@Mixin(value = Util.class, remap = false)
public class StarterStructureUtilMixin {
    private static final ThreadLocal<StarterStructureSchematic> wildernessOdysseyApi$schematic = new ThreadLocal<>();

    @Inject(
            method = "generateSchematic",
            at = @At(
                    value = "INVOKE_ASSIGN",
                    target = "Lcom/natamus/collective_common_neoforge/schematic/ParseSchematicFile;getParsedSchematicObject(Ljava/io/InputStream;Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;IZZ)Lcom/natamus/collective_common_neoforge/schematic/ParsedSchematicObject;",
                    shift = At.Shift.AFTER
            ),
            locals = LocalCapture.CAPTURE_FAILHARD
    )
    private static void wildernessOdysseyApi$restoreSchemEntities(ServerLevel serverLevel,
                                                                  CallbackInfoReturnable<BlockPos> cir,
                                                                  java.util.List<File> listOfSchematicFiles,
                                                                  File[] listOfFiles,
                                                                    File schematicFile,
                                                                    boolean automaticCenter,
                                                                    BlockPos structurePos,
                                                                    ParsedSchematicObject parsedSchematicObject,
                                                                  FileInputStream fileInputStream) {
        StarterStructureSchematic schematic = StarterStructureSchematic.capture(
                serverLevel, schematicFile.toPath(), structurePos, parsedSchematicObject);
        wildernessOdysseyApi$schematic.set(schematic);
    }

    @Inject(method = "generateSchematic", at = @At("RETURN"))
    private static void wildernessOdysseyApi$triggerTerrainReplacer(ServerLevel serverLevel, CallbackInfoReturnable<BlockPos> cir) {
        BlockPos structureOrigin = cir.getReturnValue();
        StarterStructureSchematic schematic = wildernessOdysseyApi$schematic.get();
        if (structureOrigin != null && schematic != null) {
            boolean pastedWithWorldEdit = StarterStructureWorldEditPlacer.placeWithWorldEdit(
                    serverLevel, schematic, structureOrigin);

            ModConstants.LOGGER.debug("[Starter Structure compat] Bypassing terrain replacer for bunker; running blending pass instead.");
            StarterStructureSpawnGuard.registerSpawnDenyZone(serverLevel, structureOrigin);
            StarterStructureTerrainBlender.blendPlacedStructure(serverLevel, structureOrigin,
                    schematic.footprint());

            int spawned = schematic.shouldSpawnEntities()
                    ? SchematicEntityRestorer.spawnRestoredEntities(serverLevel, schematic.entities())
                    : 0;
            if (spawned == 0) {
                ModConstants.LOGGER.debug("[Starter Structure compat] No missing schematic entities needed spawning.");
            }

            if (pastedWithWorldEdit) {
                schematic.clearParsedAfterWorldEdit();
            }
        }
        wildernessOdysseyApi$schematic.remove();
    }
}
