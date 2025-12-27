package com.thunder.wildernessodysseyapi.mixin;

import com.natamus.starterstructure_common_neoforge.util.Util;
import com.thunder.wildernessodysseyapi.Core.ModConstants;
import com.thunder.wildernessodysseyapi.WorldGen.structure.SchematicEntityRestorer;
import com.thunder.wildernessodysseyapi.WorldGen.structure.StarterStructureTerrainBlender;
import com.thunder.wildernessodysseyapi.WorldGen.structure.StarterStructureSpawnGuard;
import com.natamus.collective_common_neoforge.schematic.ParsedSchematicObject;
import com.mojang.datafixers.util.Pair;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.spongepowered.asm.mixin.injection.callback.LocalCapture;

import java.io.File;
import java.io.FileInputStream;
import java.nio.file.Path;
import java.util.List;

@Mixin(value = Util.class, remap = false)
public class StarterStructureUtilMixin {
    private static final ThreadLocal<StarterStructureTerrainBlender.Footprint> wildernessOdysseyApi$footprint =
            new ThreadLocal<>();
    private static final ThreadLocal<List<Pair<BlockPos, Entity>>> wildernessOdysseyApi$restoredEntities =
            new ThreadLocal<>();

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
        wildernessOdysseyApi$footprint.set(readFootprint(parsedSchematicObject));
        Path schematicPath = schematicFile.toPath();
        List<Pair<BlockPos, Entity>> restored = SchematicEntityRestorer.backfillEntitiesFromSchem(
                serverLevel, schematicPath, schematicFile.getName().endsWith(".nbt"), structurePos, parsedSchematicObject);
        wildernessOdysseyApi$restoredEntities.set(restored);
    }

    @Inject(method = "generateSchematic", at = @At("RETURN"))
    private static void wildernessOdysseyApi$triggerTerrainReplacer(ServerLevel serverLevel, CallbackInfoReturnable<BlockPos> cir) {
        BlockPos structureOrigin = cir.getReturnValue();
        if (structureOrigin != null) {
            ModConstants.LOGGER.debug("[Starter Structure compat] Bypassing terrain replacer for bunker; running blending pass instead.");
            StarterStructureSpawnGuard.registerSpawnDenyZone(serverLevel, structureOrigin);
            StarterStructureTerrainBlender.blendPlacedStructure(serverLevel, structureOrigin,
                    wildernessOdysseyApi$footprint.get());

            int spawned = SchematicEntityRestorer.spawnRestoredEntities(serverLevel, wildernessOdysseyApi$restoredEntities.get());
            if (spawned == 0) {
                ModConstants.LOGGER.debug("[Starter Structure compat] No missing schematic entities needed spawning.");
            }
        }
        wildernessOdysseyApi$footprint.remove();
        wildernessOdysseyApi$restoredEntities.remove();
    }

    private static StarterStructureTerrainBlender.Footprint readFootprint(ParsedSchematicObject parsed) {
        if (parsed == null) {
            return null;
        }
        try {
            int width = tryReadDimension(parsed, "getWidth", "width");
            int height = tryReadDimension(parsed, "getHeight", "height");
            int length = tryReadDimension(parsed, "getLength", "length");
            if (width > 0 && height > 0 && length > 0) {
                return new StarterStructureTerrainBlender.Footprint(width, height, length);
            }
        } catch (Exception e) {
            ModConstants.LOGGER.debug("[Starter Structure compat] Failed to read schematic dimensions for blending.", e);
        }
        return null;
    }

    private static int tryReadDimension(ParsedSchematicObject parsed, String getterName, String fieldName) throws Exception {
        try {
            java.lang.reflect.Method getter = parsed.getClass().getMethod(getterName);
            getter.setAccessible(true);
            Object result = getter.invoke(parsed);
            if (result instanceof Number number) {
                return number.intValue();
            }
        } catch (NoSuchMethodException ignored) {
            // Fall back to field lookup
        }

        java.lang.reflect.Field field = parsed.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        Object value = field.get(parsed);
        if (value instanceof Number number) {
            return number.intValue();
        }
        return -1;
    }
}
