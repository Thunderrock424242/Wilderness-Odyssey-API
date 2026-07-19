package com.thunder.wildernessodysseyapi.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.thunder.wildernessodysseyapi.weather.api.PrecipitationType;
import com.thunder.wildernessodysseyapi.weather.config.WeatherConfig;
import com.thunder.wildernessodysseyapi.weather.integration.LocalizedPrecipitationController;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Localizes vanilla's loaded-column precipitation decisions in controlled dimensions.
 *
 * <p>No NeoForge event exposes snow placement or {@code Block.handlePrecipitation}.
 * These three narrow operation wrappers retain the rest of
 * {@link ServerLevel#tickPrecipitation(BlockPos)}, including freezing, random
 * cadence, cauldrons, and modded precipitation-aware blocks. Mods replacing
 * these exact invocations may still need explicit ordering or an adapter.</p>
 */
@Mixin(ServerLevel.class)
public abstract class ServerLevelLocalizedPrecipitationMixin {

    /** Replaces the method's global rain gate with exposed local rain or snow. */
    @WrapOperation(
            method = "tickPrecipitation",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/server/level/ServerLevel;isRaining()Z"
            )
    )
    private boolean wildernessodysseyapi$useLocalizedPrecipitationGate(
            ServerLevel level,
            Operation<Boolean> original,
            @Local(argsOnly = true) BlockPos randomColumn
    ) {
        if (!WeatherConfig.dimensionEnabled(level.dimension())) {
            return original.call(level);
        }
        BlockPos surface = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING, randomColumn);
        return LocalizedPrecipitationController.get().isExposedToPrecipitation(level, surface);
    }

    /** Uses authoritative air temperature/type while retaining snow placement rules. */
    @WrapOperation(
            method = "tickPrecipitation",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/level/biome/Biome;shouldSnow(Lnet/minecraft/world/level/LevelReader;Lnet/minecraft/core/BlockPos;)Z"
            )
    )
    private boolean wildernessodysseyapi$useLocalizedSnowType(
            Biome biome,
            LevelReader levelReader,
            BlockPos position,
            Operation<Boolean> original
    ) {
        if (!(levelReader instanceof ServerLevel level)
                || !WeatherConfig.dimensionEnabled(level.dimension())) {
            return original.call(biome, levelReader, position);
        }
        return LocalizedPrecipitationController.get().precipitationTypeAt(level, position)
                == PrecipitationType.SNOW
                && wildernessodysseyapi$canPlaceSnow(levelReader, position);
    }

    /** Supplies local rain/snow to vanilla and modded block precipitation hooks. */
    @WrapOperation(
            method = "tickPrecipitation",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/level/biome/Biome;getPrecipitationAt(Lnet/minecraft/core/BlockPos;)Lnet/minecraft/world/level/biome/Biome$Precipitation;"
            )
    )
    private Biome.Precipitation wildernessodysseyapi$useLocalizedPrecipitationType(
            Biome biome,
            BlockPos position,
            Operation<Biome.Precipitation> original
    ) {
        ServerLevel level = (ServerLevel) (Object) this;
        if (!WeatherConfig.dimensionEnabled(level.dimension())) {
            return original.call(biome, position);
        }
        return switch (LocalizedPrecipitationController.get().precipitationTypeAt(level, position)) {
            case RAIN -> Biome.Precipitation.RAIN;
            case SNOW -> Biome.Precipitation.SNOW;
            case NONE -> Biome.Precipitation.NONE;
        };
    }

    @Unique
    private static boolean wildernessodysseyapi$canPlaceSnow(LevelReader level, BlockPos position) {
        if (position.getY() < level.getMinBuildHeight()
                || position.getY() >= level.getMaxBuildHeight()
                || level.getBrightness(LightLayer.BLOCK, position) >= 10) {
            return false;
        }
        BlockState state = level.getBlockState(position);
        return (state.isAir() || state.is(Blocks.SNOW))
                && Blocks.SNOW.defaultBlockState().canSurvive(level, position);
    }
}
