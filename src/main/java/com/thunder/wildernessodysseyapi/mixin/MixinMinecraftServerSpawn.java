package com.thunder.wildernessodysseyapi.mixin;

import com.thunder.wildernessodysseyapi.core.ModConstants;
import com.mojang.datafixers.util.Pair;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BiomeTags;
import net.minecraft.world.level.biome.Biome;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MinecraftServer.class)
public class MixinMinecraftServerSpawn {

    @Inject(method = "setInitialSpawn", at = @At("TAIL"))
    private static void forceOceanSpawn(ServerLevel level,
                                        net.minecraft.world.level.storage.ServerLevelData levelData,
                                        boolean bonusChest,
                                        boolean debug,
                                        CallbackInfo ci) {
        // Only run on overworld
        if (!level.dimension().equals(net.minecraft.world.level.Level.OVERWORLD)) return;

        BlockPos current = level.getSharedSpawnPos();

        // If already in ocean, nothing to do
        if (level.getBiome(current).is(BiomeTags.IS_OCEAN) ||
                level.getBiome(current).is(BiomeTags.IS_DEEP_OCEAN)) return;

        Pair<BlockPos, Holder<Biome>> result = level.findClosestBiome3d(
                biome -> biome.is(BiomeTags.IS_OCEAN) || biome.is(BiomeTags.IS_DEEP_OCEAN),
                current,
                6400,
                32,
                64
        );

        if (result != null) {
            BlockPos oceanPos = result.getFirst();
            level.setDefaultSpawnPos(oceanPos, 0f);
            levelData.setSpawn(oceanPos, 0f);
            ModConstants.LOGGER.info("[WildernessOdyssey] Forced spawn to ocean at {}", oceanPos);
        } else {
            // 6400 wasn't enough, try much further
            Pair<BlockPos, Holder<Biome>> retry = level.findClosestBiome3d(
                    biome -> biome.is(BiomeTags.IS_OCEAN) || biome.is(BiomeTags.IS_DEEP_OCEAN),
                    current,
                    25000,
                    64,
                    64
            );
            if (retry != null) {
                BlockPos oceanPos = retry.getFirst();
                level.setDefaultSpawnPos(oceanPos, 0f);
                levelData.setSpawn(oceanPos, 0f);
                ModConstants.LOGGER.info("[WildernessOdyssey] Forced spawn to ocean (extended search) at {}", oceanPos);
            }
        }
    }
}