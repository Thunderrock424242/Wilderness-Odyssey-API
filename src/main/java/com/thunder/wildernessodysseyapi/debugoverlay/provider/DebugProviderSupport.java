package com.thunder.wildernessodysseyapi.debugoverlay.provider;

import com.thunder.wildernessodysseyapi.mixin.DebugScreenOverlayAccessor;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.phys.HitResult;

import java.util.Locale;

final class DebugProviderSupport {
    private static final String[] EIGHT_WAY_DIRECTIONS = {
            "South", "Southwest", "West", "Northwest",
            "North", "Northeast", "East", "Southeast"
    };

    private DebugProviderSupport() {
    }

    static Entity camera(Minecraft minecraft) {
        return minecraft.getCameraEntity();
    }

    static BlockPos cameraBlockPos(Minecraft minecraft) {
        Entity camera = camera(minecraft);
        return camera == null ? BlockPos.ZERO : camera.blockPosition();
    }

    static HitResult blockTarget(Minecraft minecraft) {
        return ((DebugScreenOverlayAccessor) (Object) minecraft.getDebugOverlay())
                .wildernessOdysseyApi$getBlockTarget();
    }

    static HitResult fluidTarget(Minecraft minecraft) {
        return ((DebugScreenOverlayAccessor) (Object) minecraft.getDebugOverlay())
                .wildernessOdysseyApi$getFluidTarget();
    }

    static String biomeId(Minecraft minecraft, BlockPos pos) {
        if (minecraft.level == null) {
            return "N/A";
        }
        return minecraft.level.getBiome(pos)
                .unwrapKey()
                .map(key -> key.location().toString())
                .orElse("unregistered");
    }

    static String precisePosition(Entity entity) {
        if (entity == null) {
            return "N/A";
        }
        return String.format(Locale.ROOT, "%.3f / %.3f / %.3f", entity.getX(), entity.getY(), entity.getZ());
    }

    static String blockPosition(BlockPos pos) {
        return pos.getX() + " / " + pos.getY() + " / " + pos.getZ();
    }

    static String facing(Entity entity) {
        if (entity == null) {
            return "N/A";
        }
        int index = Math.floorMod((int) Math.floor(entity.getYRot() / 45.0F + 0.5F), EIGHT_WAY_DIRECTIONS.length);
        Direction cardinal = entity.getDirection();
        return String.format(Locale.ROOT, "%s (%s, yaw %.1f / pitch %.1f)",
                EIGHT_WAY_DIRECTIONS[index], cardinal.getName(), entity.getYRot(), entity.getXRot());
    }

    static String shortFacing(Entity entity) {
        if (entity == null) {
            return "N/A";
        }
        int index = Math.floorMod((int) Math.floor(entity.getYRot() / 45.0F + 0.5F), EIGHT_WAY_DIRECTIONS.length);
        return EIGHT_WAY_DIRECTIONS[index];
    }

    static String registryId(ResourceLocation location) {
        return location == null ? "unregistered" : location.toString();
    }

    static String ticksAsClock(long dayTime) {
        long ticks = Math.floorMod(dayTime, 24_000L);
        long minutes = Math.floorMod(ticks * 60L / 1_000L + 360L, 1_440L);
        return String.format(Locale.ROOT, "%02d:%02d (%d ticks)", minutes / 60L, minutes % 60L, ticks);
    }
}
