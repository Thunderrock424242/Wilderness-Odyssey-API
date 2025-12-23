package com.thunder.wildernessodysseyapi.mixin;

import com.natamus.starterstructure_common_neoforge.util.Util;
import com.thunder.wildernessodysseyapi.WorldGen.structure.StarterStructureTerrainAdapter;
import com.thunder.wildernessodysseyapi.WorldGen.structure.SchematicEntityRestorer;
import com.thunder.wildernessodysseyapi.WorldGen.structure.StarterStructureSpawnGuard;
import com.natamus.collective_common_neoforge.schematic.ParsedSchematicObject;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.spongepowered.asm.mixin.injection.callback.LocalCapture;

import java.io.File;
import java.io.FileInputStream;
import java.nio.file.Path;

@Mixin(value = Util.class, remap = false)
public class StarterStructureUtilMixin {
    @Inject(
            method = "generateSchematic",
            at = @At(
                    value = "INVOKE_ASSIGN",
                    target = "Lcom/natamus/collective_common_neoforge/schematic/ParseSchematicFile;getParsedSchematicObject(Ljava/io/InputStream;Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;IZZ)Lcom/natamus/collective_common_neoforge/schematic/ParsedSchematicObject;"
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
        Path schematicPath = schematicFile.toPath();
        SchematicEntityRestorer.backfillEntitiesFromSchem(serverLevel, schematicPath, schematicFile.getName().endsWith(".nbt"),
                structurePos, parsedSchematicObject);
    }

    @Inject(method = "generateSchematic", at = @At("RETURN"))
    private static void wildernessOdysseyApi$triggerTerrainReplacer(ServerLevel serverLevel, CallbackInfoReturnable<BlockPos> cir) {
        BlockPos structureOrigin = cir.getReturnValue();
        if (structureOrigin != null) {
            StarterStructureTerrainAdapter.scheduleTerrainReplacement(serverLevel, structureOrigin);
            StarterStructureSpawnGuard.registerSpawnDenyZone(serverLevel, structureOrigin);
        }
    }
}
