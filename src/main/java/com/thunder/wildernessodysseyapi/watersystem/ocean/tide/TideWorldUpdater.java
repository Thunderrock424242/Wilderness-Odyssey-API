package com.thunder.wildernessodysseyapi.watersystem.ocean.tide;

import com.thunder.wildernessodysseyapi.watersystem.water.wave.WaterBodyClassifier;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.LevelTickEvent;

/**
 * TideWorldUpdater
 *
 * Applies tide-level changes to ocean water blocks in loaded chunks.
 * Runs on the server every 100 ticks (5 seconds) to limit performance impact.
 *
 * Logic:
 *   - Get current tide offset from TideSystem
 *   - For ocean blocks at sea level (Y=62): if tide > 0.5 fill one block higher
 *   - For ocean blocks above sea level:     if tide < -0.5 remove them
 *   - Smooth transition prevents jarring pop-in
 *
 * This does NOT move the actual Minecraft sea level — it adds/removes
 * individual water source blocks at the shoreline, which is both cheaper
 * and more natural looking.
 */
@EventBusSubscriber(modid = "wildernessodysseyapi", bus = EventBusSubscriber.Bus.GAME)
public class TideWorldUpdater {

    private static final int SEA_LEVEL        = 62;
    private static final int TIDE_CHECK_RANGE = 24;  // blocks around players
    private static final int TICK_INTERVAL    = 100; // ticks between updates
    private static final int MAX_TIDE_STEPS   = 2;   // keep in sync with TideSystem amplitude

    private static float lastTideOffset = 0f;
    private static int   tickCounter    = 0;

    @SubscribeEvent
    public static void onLevelTick(LevelTickEvent.Post event) {
        if (!(event.getLevel() instanceof ServerLevel level)) return;

        tickCounter++;
        if (tickCounter < TICK_INTERVAL) return;
        tickCounter = 0;

        float tideOffset = TideSystem.getTideOffset(level);
        float delta      = tideOffset - lastTideOffset;
        lastTideOffset   = tideOffset;

        // Only do expensive work if tide changed meaningfully
        if (Math.abs(delta) < 0.05f) return;

        applyTideToOceanShores(level, tideOffset);
    }

    private static void applyTideToOceanShores(ServerLevel level, float tideOffset) {
        level.players().forEach(player -> {
            BlockPos origin = player.blockPosition();

            for (int dx = -TIDE_CHECK_RANGE; dx <= TIDE_CHECK_RANGE; dx += 4) {
                for (int dz = -TIDE_CHECK_RANGE; dz <= TIDE_CHECK_RANGE; dz += 4) {
                    BlockPos surface = findShoreline(level,
                            origin.getX() + dx, origin.getZ() + dz);
                    if (surface == null) continue;

                    if (WaterBodyClassifier.classify(level, surface)
                            != WaterBodyClassifier.WaterType.OCEAN) continue;

                    int targetY = clampTargetY(SEA_LEVEL + Math.round(tideOffset));
                    applyColumnTide(level, surface, targetY);
                }
            }
        });
    }

    private static int clampTargetY(int y) {
        return Math.max(SEA_LEVEL - MAX_TIDE_STEPS, Math.min(SEA_LEVEL + MAX_TIDE_STEPS, y));
    }

    /**
     * Raise or lower one sampled shoreline column to match the target tide height.
     * This keeps transitions smoother than one-off threshold fills/drains and
     * actually uses the computed targetY for both rising and falling tides.
     */
    private static void applyColumnTide(ServerLevel level, BlockPos surface, int targetY) {
        int topY = surface.getY();

        if (topY < targetY) {
            for (int y = topY + 1; y <= targetY; y++) {
                BlockPos pos = new BlockPos(surface.getX(), y, surface.getZ());
                BlockState state = level.getBlockState(pos);
                if (state.isAir()) {
                    level.setBlock(pos, Blocks.WATER.defaultBlockState(), 2);
                }
            }
            return;
        }

        if (topY > targetY) {
            for (int y = topY; y > targetY; y--) {
                BlockPos pos = new BlockPos(surface.getX(), y, surface.getZ());
                if (level.getFluidState(pos).is(Fluids.WATER)) {
                    level.setBlock(pos, Blocks.AIR.defaultBlockState(), 2);
                }
            }
        }
    }

    /**
     * Find the topmost water block surface at (x, z).
     * Returns null if no water found in Y range.
     */
    private static BlockPos findShoreline(ServerLevel level, int x, int z) {
        for (int y = SEA_LEVEL + 3; y >= SEA_LEVEL - 2; y--) {
            BlockPos pos = new BlockPos(x, y, z);
            if (level.getFluidState(pos).is(Fluids.WATER)) {
                return pos;
            }
        }
        return null;
    }
}
